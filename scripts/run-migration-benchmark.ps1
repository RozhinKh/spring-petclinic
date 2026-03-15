param(
    [string]$Variant = "java21-virtual",
    [int]$LoadTestDurationSeconds = 30,
    [string]$ResultsRoot = "migration-benchmark-results",
    [string]$BaselineEvidenceDir = "benchmark-evidence\java17-baseline",
    [string]$BaselineRef = "e1e80ed",
    [string]$MigrationRef = "HEAD"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$resultsDir = Join-Path $projectRoot "$ResultsRoot\$timestamp-$Variant"
$rawDir = Join-Path $resultsDir "raw"
$testResultsDir = Join-Path $resultsDir "tests"
$coverageDir = Join-Path $resultsDir "coverage"
$jfrPath = Join-Path $rawDir "recording.jfr"
$jfrJsonPath = Join-Path $rawDir "recording.json"
$loadResultsPath = Join-Path $rawDir "load-test-results.json"
$metricsPath = Join-Path $rawDir "metrics.jsonl"
$jmhResultsPath = Join-Path $rawDir "jmh-results.json"
$spotbugsSummaryPath = Join-Path $rawDir "spotbugs-blocking-summary.json"
$summaryPath = Join-Path $resultsDir "migration-benchmark-summary.json"
$metricsReportPath = Join-Path $resultsDir "migration-metrics.json"
$reportPath = Join-Path $resultsDir "MIGRATION-BENCHMARK-RESULTS.txt"
$jarPath = Join-Path $projectRoot "target\spring-petclinic-4.0.0-SNAPSHOT.jar"
$baselineManifestPath = Join-Path (Join-Path $projectRoot $BaselineEvidenceDir) "manifest.json"
$baselineTestsDir = Join-Path (Join-Path $projectRoot $BaselineEvidenceDir) "tests"
$baselineBlockingPath = Join-Path (Join-Path $projectRoot $BaselineEvidenceDir) "runs\stable-load\spotbugs-blocking-summary.json"

New-Item -ItemType Directory -Path $resultsDir -Force | Out-Null
New-Item -ItemType Directory -Path $rawDir -Force | Out-Null
New-Item -ItemType Directory -Path $testResultsDir -Force | Out-Null
New-Item -ItemType Directory -Path $coverageDir -Force | Out-Null

function Stop-Port8080Processes {
    Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique |
        ForEach-Object {
            Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
        }
    Start-Sleep -Seconds 2
}

function Wait-ForHealth([int]$TimeoutSeconds = 90) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200) {
                return $true
            }
        }
        catch {
        }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Get-MavenProfileArgs([string]$selectedVariant) {
    switch ($selectedVariant) {
        "java21-traditional" { return @("-Pjava21-traditional") }
        "java21-virtual" { return @("-Pjava21-virtual") }
        default { return @() }
    }
}

function Get-AppProfiles([string]$selectedVariant) {
    switch ($selectedVariant) {
        "java21-traditional" { return "benchmark,java21-traditional" }
        "java21-virtual" { return "benchmark,java21-virtual,vthreads" }
        default { return "benchmark" }
    }
}

function Start-App([string]$logPath, [string[]]$extraJvmArgs = @()) {
    Stop-Port8080Processes
    $quotedJarPath = '"' + $jarPath + '"'
    $profiles = Get-AppProfiles $Variant
    $argList = @("-Xms256m", "-Xmx512m") + $extraJvmArgs + @("-jar", $quotedJarPath, "--spring.profiles.active=$profiles")
    $stdoutPath = $logPath
    $stderrPath = [System.IO.Path]::ChangeExtension($logPath, ".err.log")
    $process = Start-Process -FilePath "java" -ArgumentList $argList -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -PassThru -WindowStyle Hidden
    if (-not (Wait-ForHealth 120)) {
        if (-not $process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        }
        throw "Application did not become healthy within timeout. See $stdoutPath and $stderrPath"
    }
    return $process
}

function Stop-App([System.Diagnostics.Process]$process, [string]$jfrDumpPath = $null) {
    if ($null -eq $process) {
        return
    }
    if (-not $process.HasExited) {
        if ($jfrDumpPath) {
            try {
                $tempJfrPath = Join-Path $env:TEMP ("petclinic-" + [System.IO.Path]::GetFileName($jfrDumpPath))
                & jcmd $process.Id JFR.dump "filename=$tempJfrPath" | Out-Null
                Start-Sleep -Seconds 2
                if (Test-Path $tempJfrPath) {
                    Copy-Item $tempJfrPath $jfrDumpPath -Force
                }
            }
            catch {
            }
        }
        Stop-Process -Id $process.Id -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 3
    }
}

function Measure-Startup([string]$label, [string]$logPath) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $process = $null
    try {
        $process = Start-App -logPath $logPath
        $sw.Stop()
        return [ordered]@{
            label = $label
            startup_ms = [math]::Round($sw.Elapsed.TotalMilliseconds, 0)
            success = $true
        }
    }
    finally {
        if ($process) {
            Stop-App $process
        }
    }
}

function Invoke-MavenWithStatus([string[]]$goalsAndArgs) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = Join-Path $projectRoot "mvnw.cmd"
    $psi.WorkingDirectory = $projectRoot
    $psi.Arguments = ($goalsAndArgs -join " ")
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $process = [System.Diagnostics.Process]::Start($psi)
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    [ordered]@{
        exit_code = $process.ExitCode
        stdout = $stdout
        stderr = $stderr
    }
}

function Get-JsonMetricValue([string]$metricName) {
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/$metricName" -TimeoutSec 5
        if ($response.measurements.Count -gt 0) {
            return $response.measurements[0].value
        }
    }
    catch {
    }
    return $null
}

function Capture-ActuatorSnapshot {
    [ordered]@{
        ts = [DateTime]::UtcNow.ToString("o")
        http_requests_count = Get-JsonMetricValue "http.server.requests"
        jvm_memory_heap_used = Get-JsonMetricValue "jvm.memory.used?tag=area:heap"
        jvm_memory_heap_max = Get-JsonMetricValue "jvm.memory.max?tag=area:heap"
        jvm_threads_live = Get-JsonMetricValue "jvm.threads.live"
        process_cpu_usage = Get-JsonMetricValue "process.cpu.usage"
        jvm_gc_pause_total = Get-JsonMetricValue "jvm.gc.pause"
        jvm_gc_memory_allocated = Get-JsonMetricValue "jvm.gc.memory.allocated"
        jvm_gc_memory_promoted = Get-JsonMetricValue "jvm.gc.memory.promoted"
        hikaricp_connections_active = Get-JsonMetricValue "hikaricp.connections.active"
    }
}

function Get-TextLineCount([string]$text) {
    if ([string]::IsNullOrEmpty($text)) {
        return 0
    }
    return (@($text -split "`r?`n")).Count
}

function Get-GitFileLineCount([string]$gitRef, [string]$path) {
    $content = git show "${gitRef}:$path" 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrEmpty($content)) {
        return 0
    }
    return Get-TextLineCount $content
}

function Get-SourceConstructMetrics {
    $javaFiles = Get-ChildItem -Path (Join-Path $projectRoot "src\main\java"), (Join-Path $projectRoot "src\test\java") -Recurse -Filter *.java
    $totalJavaLoc = 0
    $patternMatches = 0
    $switchExpressions = 0
    $virtualThreadRefs = 0
    $recordDeclarations = 0
    foreach ($file in $javaFiles) {
        $content = Get-Content $file.FullName
        $totalJavaLoc += $content.Count
        $recordDeclarations += @($content | Select-String -Pattern '^\s*(public\s+)?record\s+').Count
        $patternMatches += @($content | Select-String -Pattern 'instanceof\s+\w+\s+\w+|ifPresentOrElse\s*\(|case\s+\w+\s+\w+\s+when').Count
        $switchExpressions += @($content | Select-String -Pattern '\byield\b').Count
        $virtualThreadRefs += @($content | Select-String -Pattern 'newVirtualThreadPerTaskExecutor|VirtualThread|spring\.threads\.virtual|virtual-threads').Count
    }
    [ordered]@{
        total_java_loc_after = $totalJavaLoc
        record_declarations_after = $recordDeclarations
        pattern_matching_usages_after = $patternMatches
        switch_expression_markers_after = $switchExpressions
        virtual_thread_references_after = $virtualThreadRefs
    }
}

function Get-GitMigrationMetrics([string]$baseline, [string]$afterRef) {
    $allChangedFiles = @(git diff --name-only $baseline $afterRef -- "src/main/java" "src/test/java" "src/main/resources" "pom.xml" "build.gradle" "run_tests.sh")
    $javaChangedFiles = @($allChangedFiles | Where-Object { $_ -match '\.java$' })
    $packageNames = New-Object System.Collections.Generic.HashSet[string]
    foreach ($file in $javaChangedFiles) {
        $dir = [System.IO.Path]::GetDirectoryName($file)
        if ($dir) {
            $normalized = $dir.Replace('\', '/')
            if ($normalized -match 'src/(main|test)/java/(.+)$') {
                [void]$packageNames.Add(($matches[2] -replace '/', '.'))
            }
        }
    }

    $numstatLines = @(git diff --numstat $baseline $afterRef -- "src/main/java" "src/test/java")
    $added = 0
    $deleted = 0
    foreach ($line in $numstatLines) {
        if ($line -match '^(\d+)\s+(\d+)\s+') {
            $added += [int]$matches[1]
            $deleted += [int]$matches[2]
        }
    }

    $baselineJavaFiles = @(git ls-tree -r --name-only $baseline -- "src/main/java" "src/test/java")
    $baselineJavaLoc = 0
    foreach ($file in $baselineJavaFiles) {
        $baselineJavaLoc += Get-GitFileLineCount $baseline $file
    }

    $recordDiff = git diff $baseline $afterRef -- "src/main/java" "src/test/java"
    $recordsIntroduced = @($recordDiff | Select-String -Pattern '^\+\s*(public\s+)?record\s+').Count
    $patternUsagesIntroduced = @($recordDiff | Select-String -Pattern '^\+.*(instanceof\s+\w+\s+\w+|ifPresentOrElse\s*\(|case\s+\w+\s+\w+\s+when)').Count
    $switchConversions = @($recordDiff | Select-String -Pattern '^\+.*\byield\b').Count
    $virtualThreadPoints = @($recordDiff | Select-String -Pattern '^\+.*(newVirtualThreadPerTaskExecutor|spring\.threads\.virtual|virtual-threads|VirtualThread)').Count
    $deprecatedApiReplacements = @($recordDiff | Select-String -Pattern '^\-.*@Deprecated|^\-.*Thread\.stop|^\-.*ThreadGroup').Count
    $removedLegacyConstructs = @($recordDiff | Select-String -Pattern '^\-.*(newFixedThreadPool|newCachedThreadPool|synchronized\s*\(|wait\(|notify\(|CompletableFuture)').Count

    $buildFiles = @("pom.xml", "build.gradle", "run_tests.sh", "mvnw", "mvnw.cmd", ".mvn/wrapper/maven-wrapper.properties")
    $changedBuildFiles = @(git diff --name-only $baseline $afterRef -- $buildFiles)
    $buildDiff = git diff $baseline $afterRef -- $buildFiles
    $dependencyUpgrades = @($buildDiff | Select-String -Pattern '^\+.*(<artifactId>|<groupId>|<version>|implementation |runtimeOnly |testImplementation )').Count

    [ordered]@{
        baseline_ref = $baseline
        after_ref = $afterRef
        baseline_java_loc = $baselineJavaLoc
        lines_of_code_migrated_refactored = ($added + $deleted)
        lines_added = $added
        lines_deleted = $deleted
        percentage_of_codebase_modified = if ($baselineJavaLoc -gt 0) { [math]::Round((($added + $deleted) / $baselineJavaLoc) * 100, 2) } else { $null }
        number_of_files_modified = $allChangedFiles.Count
        number_of_java_files_modified = $javaChangedFiles.Count
        number_of_modules_or_packages_touched = $packageNames.Count
        packages_touched = @($packageNames | Sort-Object)
        records_introduced = $recordsIntroduced
        pattern_matching_usages_introduced = $patternUsagesIntroduced
        switch_expression_or_pattern_switch_conversions = $switchConversions
        virtual_thread_usage_points_introduced = $virtualThreadPoints
        deprecated_api_replacements = $deprecatedApiReplacements
        removed_legacy_constructs = $removedLegacyConstructs
        dependency_upgrades_required_for_java21 = $dependencyUpgrades
        number_of_build_configuration_changes = $changedBuildFiles.Count
        build_files_changed = $changedBuildFiles
    }
}

function Parse-JfrMetrics {
    if (-not (Test-Path $jfrPath)) {
        return @{}
    }
    $file = Get-Item $jfrPath
    if ($file.Length -le 0) {
        return @{}
    }

    try {
        & jfr print --json $jfrPath | Set-Content -Path $jfrJsonPath
        $jfrJson = Get-Content $jfrJsonPath -Raw | ConvertFrom-Json
        $events = @($jfrJson.recording.events)
        $blockingEvents = @($events | Where-Object { $_.type.name -in @("jdk.JavaMonitorWait", "jdk.ThreadPark") })
        $gcEvents = @($events | Where-Object { $_.type.name -eq "jdk.GarbageCollection" })
        $allocationEvents = @($events | Where-Object { $_.type.name -eq "jdk.ObjectAllocationSample" })
        $gcPauseMs = 0.0
        foreach ($event in $gcEvents) {
            if ($event.values.duration) {
                $gcPauseMs += [double]$event.values.duration / 1000000.0
            }
        }

        return [ordered]@{
            runtime_blocking_event_count = $blockingEvents.Count
            gc_pause_total_ms = [math]::Round($gcPauseMs, 2)
            sampled_allocation_events = $allocationEvents.Count
        }
    }
    catch {
        $blockingCount = (jfr print --events jdk.JavaMonitorWait,jdk.ThreadPark $jfrPath | Select-String -Pattern '^jdk\.(JavaMonitorWait|ThreadPark) \{').Count
        return [ordered]@{
            runtime_blocking_event_count = $blockingCount
        }
    }
}

function Parse-SpotBugsSummary {
    $spotbugsCandidates = @(
        (Join-Path $projectRoot "target\spotbugsXml.xml"),
        (Join-Path $projectRoot "target\spotbugs\spotbugs.xml")
    )
    $spotbugsXml = $spotbugsCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $spotbugsXml) {
        return @{}
    }

    [xml]$xml = Get-Content $spotbugsXml
    $blockingBugCodes = @("DM_MONITORENTER", "JLM_JSR166_LOCK_MONITORENTER", "NN_NAKED_NOTIFY", "SWL_SLEEP_WITH_LOCK_HELD")
    $allBugs = @($xml.BugCollection.BugInstance)
    $blockingBugs = @($allBugs | Where-Object {
        $message = "$($_.LongMessage) $($_.ShortMessage) $($_.message)"
        ($blockingBugCodes -contains $_.type) -or
        ($blockingBugCodes -contains $_.abbrev) -or
        ($message -match 'blocking|lock|synchronized|monitor|wait|sleep')
    })

    $summary = [ordered]@{
        report_file = $spotbugsXml
        total_bug_instances = $allBugs.Count
        blocking_bug_instances = $blockingBugs.Count
    }
    $summary | ConvertTo-Json | Set-Content -Path $spotbugsSummaryPath
    return $summary
}

function Run-JmhBenchmarks {
    $classpathFile = Join-Path $projectRoot "target\jmh-classpath.txt"
    & "$projectRoot\mvnw.cmd" -q "-Dmaven.test.skip=true" dependency:build-classpath "-Dmdep.outputFile=$classpathFile" | Out-Null
    $classpath = (Get-Content $classpathFile -Raw).Trim()
    if (-not $classpath) {
        throw "Failed to build JMH classpath"
    }
    $fullClasspath = "target\classes;target\test-classes;$classpath"
    & java -cp $fullClasspath org.openjdk.jmh.Main `
        org.springframework.samples.petclinic.benchmark.LatencyBenchmark `
        org.springframework.samples.petclinic.benchmark.ThroughputBenchmark `
        -f 1 -wi 2 -i 3 -r 1s -rf json -rff $jmhResultsPath
    return (Get-Content $jmhResultsPath -Raw | ConvertFrom-Json)
}

function Summarize-JmhResults($jmhResults) {
    $latency = @($jmhResults | Where-Object { $_.benchmark -like "*LatencyBenchmark*" })
    $throughput = @($jmhResults | Where-Object { $_.benchmark -like "*ThroughputBenchmark*" })
    [ordered]@{
        latency = @($latency | ForEach-Object {
            [ordered]@{
                benchmark = $_.benchmark
                mode = $_.mode
                score = $_.primaryMetric.score
                unit = $_.primaryMetric.scoreUnit
            }
        })
        throughput = @($throughput | ForEach-Object {
            [ordered]@{
                benchmark = $_.benchmark
                mode = $_.mode
                score = $_.primaryMetric.score
                unit = $_.primaryMetric.scoreUnit
            }
        })
    }
}

function Copy-IfExists([string]$source, [string]$destination) {
    if (Test-Path $source) {
        Copy-Item $source $destination -Force -Recurse
    }
}

function Get-JUnitSummary([string]$reportsDir) {
    $reportFiles = @(Get-ChildItem -Path $reportsDir -Filter "TEST-*.xml" -ErrorAction SilentlyContinue)
    $tests = 0
    $failures = 0
    $errors = 0
    $skipped = 0
    $timeSeconds = 0.0
    $failingTests = New-Object System.Collections.Generic.HashSet[string]
    foreach ($file in $reportFiles) {
        [xml]$xml = Get-Content $file.FullName
        $suite = $xml.testsuite
        if ($suite) {
            $tests += [int]$suite.tests
            $failures += [int]$suite.failures
            $errors += [int]$suite.errors
            $skipped += [int]$suite.skipped
            $timeSeconds += [double]$suite.time
        }
        foreach ($case in @($xml.testsuite.testcase)) {
            if ($case.failure -or $case.error) {
                [void]$failingTests.Add("$($case.classname).$($case.name)")
            }
        }
    }
    [ordered]@{
        tests = $tests
        failures = $failures
        errors = $errors
        skipped = $skipped
        passed = [math]::Max($tests - $failures - $errors - $skipped, 0)
        total_time_s = [math]::Round($timeSeconds, 2)
        pass_rate_pct = if ($tests -gt 0) { [math]::Round((([math]::Max($tests - $failures - $errors, 0)) / $tests) * 100, 2) } else { $null }
        failing_tests = @($failingTests | Sort-Object)
    }
}

function Get-JaCoCoLineCoverage([string]$jacocoXmlPath) {
    if (-not (Test-Path $jacocoXmlPath)) {
        return $null
    }
    [xml]$xml = Get-Content $jacocoXmlPath
    $lineCounter = @($xml.report.counter | Where-Object { $_.type -eq "LINE" } | Select-Object -First 1)
    if (-not $lineCounter) {
        return $null
    }
    $missed = [double]$lineCounter.missed
    $covered = [double]$lineCounter.covered
    if (($missed + $covered) -eq 0) {
        return $null
    }
    return [math]::Round(($covered / ($covered + $missed)) * 100, 2)
}

function Get-CurrentCommit {
    (git rev-parse HEAD).Trim()
}

Push-Location $projectRoot
try {
    $profileArgs = Get-MavenProfileArgs $Variant
    Write-Host "Building migration benchmark package for $Variant..."
    $buildArgs = @(
        "-q",
        "clean",
        "package",
        "-Dmaven.test.skip=true",
        "-Dcheckstyle.skip=true",
        "-Dnohttp.skip=true",
        "-Dspring-javaformat.skip=true"
    ) + $profileArgs
    & "$projectRoot\mvnw.cmd" @buildArgs

    if (-not (Test-Path $jarPath)) {
        throw "Built jar not found at $jarPath"
    }

    $constructMetrics = Get-SourceConstructMetrics
    $gitMetrics = Get-GitMigrationMetrics -baseline $BaselineRef -afterRef $MigrationRef
    $baselineManifest = Get-Content $baselineManifestPath -Raw | ConvertFrom-Json
    $baselineSpotbugs = Get-Content $baselineBlockingPath -Raw | ConvertFrom-Json
    $baselineTests = Get-JUnitSummary $baselineTestsDir

    $coldStartup = Measure-Startup -label "cold" -logPath (Join-Path $rawDir "cold-start.log")
    $warmStartup = Measure-Startup -label "warm" -logPath (Join-Path $rawDir "warm-start.log")

    Write-Host "Starting application with JFR for migration benchmark..."
    $jfrRecordingArg = '"-XX:StartFlightRecording=settings=profile,maxsize=256m,dumponexit=true,filename=' + $jfrPath + '"'
    $appProcess = Start-App -logPath (Join-Path $rawDir "app.log") -extraJvmArgs @($jfrRecordingArg)
    try {
        Write-Host "Running load test..."
        $loadTestOut = Join-Path $rawDir "load-test.log"
        $loadTestErr = Join-Path $rawDir "load-test.err.log"
        $loadTestScript = '"' + (Join-Path $projectRoot "scripts\load-test.py") + '"'
        $quotedLoadResults = '"' + $loadResultsPath + '"'
        $loadTestProcess = Start-Process -FilePath "python" -ArgumentList @($loadTestScript, "http://localhost:8080", $quotedLoadResults, $LoadTestDurationSeconds.ToString()) -RedirectStandardOutput $loadTestOut -RedirectStandardError $loadTestErr -PassThru -WindowStyle Hidden

        $snapshots = @()
        while (-not $loadTestProcess.HasExited -and $snapshots.Count -lt 6) {
            $snapshot = Capture-ActuatorSnapshot
            $snapshots += $snapshot
            ($snapshot | ConvertTo-Json -Compress) | Add-Content -Path $metricsPath
            Start-Sleep -Seconds 10
            $loadTestProcess.Refresh()
        }
        Wait-Process -Id $loadTestProcess.Id
        Write-Host "Running JMH latency and throughput benchmarks..."
        $jmhResults = Run-JmhBenchmarks
    }
    finally {
        Stop-App $appProcess $jfrPath
    }
    Write-Host "Running tests, JaCoCo, and SpotBugs for post-migration metrics..."
    $verifyArgs = @(
        "-q",
        "verify",
        "-Dmaven.test.failure.ignore=true",
        "-Dspotbugs.failOnError=false",
        "-Dcheckstyle.skip=true",
        "-Dnohttp.skip=true",
        "-Dspring-javaformat.skip=true"
    ) + $profileArgs
    $verifyResult = Invoke-MavenWithStatus $verifyArgs
    Set-Content -Path (Join-Path $rawDir "verify.stdout.log") -Value $verifyResult.stdout
    Set-Content -Path (Join-Path $rawDir "verify.stderr.log") -Value $verifyResult.stderr

    Copy-IfExists (Join-Path $projectRoot "target\surefire-reports") $testResultsDir
    Copy-IfExists (Join-Path $projectRoot "target\site\jacoco\jacoco.xml") (Join-Path $coverageDir "jacoco.xml")
    Copy-IfExists (Join-Path $projectRoot "target\jacoco.exec") (Join-Path $coverageDir "jacoco.exec")

    $loadResults = Get-Content $loadResultsPath -Raw | ConvertFrom-Json
    $jmhSummary = Summarize-JmhResults $jmhResults
    $jfrMetrics = Parse-JfrMetrics
    $spotbugsSummary = Parse-SpotBugsSummary
    $currentTests = Get-JUnitSummary $testResultsDir
    $jacocoLineCoverageAfter = Get-JaCoCoLineCoverage (Join-Path $coverageDir "jacoco.xml")
    $snapshots = Get-Content $metricsPath | ForEach-Object { $_ | ConvertFrom-Json }

    $heapValues = @($snapshots | ForEach-Object { [double]$_.jvm_memory_heap_used } | Where-Object { $_ -gt 0 })
    $allocValues = @($snapshots | ForEach-Object { [double]$_.jvm_gc_memory_allocated } | Where-Object { $_ -gt 0 })
    $promoValues = @($snapshots | ForEach-Object { [double]$_.jvm_gc_memory_promoted } | Where-Object { $_ -gt 0 })
    $timeWindowSeconds = if ($snapshots.Count -ge 2) {
        ([DateTime]$snapshots[-1].ts - [DateTime]$snapshots[0].ts).TotalSeconds
    }
    else {
        0
    }

    $allocationRateMbPerSec = if ($allocValues.Count -ge 2 -and $timeWindowSeconds -gt 0) {
        [math]::Round((($allocValues[-1] - $allocValues[0]) / 1MB) / $timeWindowSeconds, 2)
    }
    else {
        $null
    }
    $promotionRateMbPerSec = if ($promoValues.Count -ge 2 -and $timeWindowSeconds -gt 0) {
        [math]::Round((($promoValues[-1] - $promoValues[0]) / 1MB) / $timeWindowSeconds, 2)
    }
    else {
        $null
    }

    $baselineFailingTests = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($item in $baselineTests.failing_tests) {
        [void]$baselineFailingTests.Add($item)
    }
    $currentFailingTests = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($item in $currentTests.failing_tests) {
        [void]$currentFailingTests.Add($item)
    }

    $introducedFailures = @($currentFailingTests | Where-Object { -not $baselineFailingTests.Contains($_) } | Sort-Object)
    $resolvedFailures = @($baselineFailingTests | Where-Object { -not $currentFailingTests.Contains($_) } | Sort-Object)

    $migrationMetrics = [ordered]@{
        label = "migration-benchmark"
        variant = $Variant
        benchmark_timestamp = (Get-Date).ToUniversalTime().ToString("o")
        baseline_reference = [ordered]@{
            evidence_dir = $BaselineEvidenceDir
            git_ref = $BaselineRef
        }
        post_migration_reference = [ordered]@{
            git_ref = $MigrationRef
            workspace_commit = Get-CurrentCommit
        }
        migration_effort_metrics = [ordered]@{
            total_java_loc_in_project = $constructMetrics.total_java_loc_after
            baseline_java_loc_in_project = $gitMetrics.baseline_java_loc
            lines_of_code_migrated_refactored = $gitMetrics.lines_of_code_migrated_refactored
            percentage_of_codebase_modified = $gitMetrics.percentage_of_codebase_modified
            number_of_files_modified = $gitMetrics.number_of_files_modified
            number_of_java_files_modified = $gitMetrics.number_of_java_files_modified
            number_of_modules_or_packages_touched = $gitMetrics.number_of_modules_or_packages_touched
            packages_touched = $gitMetrics.packages_touched
            records_introduced = $gitMetrics.records_introduced
            pattern_matching_usages_introduced = $gitMetrics.pattern_matching_usages_introduced
            switch_expression_or_pattern_switch_conversions = $gitMetrics.switch_expression_or_pattern_switch_conversions
            virtual_thread_usage_points_introduced = $gitMetrics.virtual_thread_usage_points_introduced
            deprecated_api_replacements = $gitMetrics.deprecated_api_replacements
            removed_legacy_constructs = $gitMetrics.removed_legacy_constructs
            dependency_upgrades_required_for_java21 = $gitMetrics.dependency_upgrades_required_for_java21
            number_of_build_configuration_changes = $gitMetrics.number_of_build_configuration_changes
            build_files_changed = $gitMetrics.build_files_changed
            static_analysis_issues_before_migration = $baselineSpotbugs.total_bug_instances
            static_analysis_issues_after_migration = $spotbugsSummary.total_bug_instances
            blocking_related_findings_before_migration = [ordered]@{
                static = $baselineSpotbugs.blocking_bug_instances
                runtime = "See baseline artifact_complete JFR evidence"
            }
            blocking_related_findings_after_migration = [ordered]@{
                static = $spotbugsSummary.blocking_bug_instances
                runtime = $jfrMetrics.runtime_blocking_event_count
            }
            test_suite_pass_rate_before_migration = $baselineTests.pass_rate_pct
            test_suite_pass_rate_after_migration = $currentTests.pass_rate_pct
            test_failures_introduced_by_migration = $introducedFailures
            test_failures_resolved_during_migration = $resolvedFailures
            total_test_suite_execution_time_before_migration_s = $baselineTests.total_time_s
            total_test_suite_execution_time_after_migration_s = $currentTests.total_time_s
            code_coverage_before_migration_pct = $baselineManifest.coverage.line_coverage_pct
            code_coverage_after_migration_pct = $jacocoLineCoverageAfter
            number_of_migration_errors_encountered = if ($verifyResult.exit_code -eq 0) { 0 } else { 1 }
            number_of_rework_cycles_required = "not_tracked"
            estimated_manual_migration_effort = "not_tracked"
            actual_migration_effort_with_artemis = "not_tracked"
            time_spent_debugging_migration_issues = "not_tracked"
            percentage_of_migration_automated_by_artemis = "not_tracked"
            manual_code_review_effort_reduction = "not_tracked"
            cves_resolved_by_upgrading_runtime_or_dependencies = "not_tracked"
        }
        actual_post_migration_code_shape = [ordered]@{
            record_declarations_after = $constructMetrics.record_declarations_after
            pattern_matching_usages_after = $constructMetrics.pattern_matching_usages_after
            switch_expression_markers_after = $constructMetrics.switch_expression_markers_after
            virtual_thread_references_after = $constructMetrics.virtual_thread_references_after
        }
        runtime_benchmark = [ordered]@{
            startup = [ordered]@{
                cold_ms = $coldStartup.startup_ms
                warm_ms = $warmStartup.startup_ms
            }
            load_test = [ordered]@{
                users_100 = ($loadResults | Where-Object concurrency -eq 100 | Select-Object -First 1)
                users_250 = ($loadResults | Where-Object concurrency -eq 250 | Select-Object -First 1)
                users_500 = ($loadResults | Where-Object concurrency -eq 500 | Select-Object -First 1)
            }
            actuator = [ordered]@{
                heap_avg_mb = if ($heapValues.Count -gt 0) { [math]::Round((($heapValues | Measure-Object -Average).Average / 1MB), 2) } else { $null }
                heap_peak_mb = if ($heapValues.Count -gt 0) { [math]::Round((($heapValues | Measure-Object -Maximum).Maximum / 1MB), 2) } else { $null }
                allocation_rate_mb_per_sec = $allocationRateMbPerSec
                promotion_rate_mb_per_sec = $promotionRateMbPerSec
                allocation_pressure_mb = if ($allocValues.Count -gt 0) { [math]::Round(($allocValues[-1] / 1MB), 2) } else { $null }
                promotion_total_mb = if ($promoValues.Count -gt 0) { [math]::Round(($promoValues[-1] / 1MB), 2) } else { $null }
            }
            jfr = $jfrMetrics
            jmh = $jmhSummary
            spotbugs = $spotbugsSummary
        }
        artifact_locations = [ordered]@{
            summary = $summaryPath
            raw = $rawDir
            tests = $testResultsDir
            coverage = $coverageDir
        }
    }

    $migrationMetrics | ConvertTo-Json -Depth 10 | Set-Content -Path $metricsReportPath
    $migrationMetrics | ConvertTo-Json -Depth 10 | Set-Content -Path $summaryPath
    @(
        "MIGRATION BENCHMARK RESULTS - $Variant",
        "Label: migration-benchmark",
        "Results directory: $resultsDir",
        "",
        "Migration effort",
        "Total Java LOC after migration: $($constructMetrics.total_java_loc_after)",
        "Baseline Java LOC: $($gitMetrics.baseline_java_loc)",
        "Changed LOC across migration diff: $($gitMetrics.lines_of_code_migrated_refactored)",
        "Codebase modified: $($gitMetrics.percentage_of_codebase_modified)%",
        "Files modified: $($gitMetrics.number_of_files_modified)",
        "Packages touched: $($gitMetrics.number_of_modules_or_packages_touched)",
        "Records introduced (diff): $($gitMetrics.records_introduced)",
        "Pattern matching introduced (diff markers): $($gitMetrics.pattern_matching_usages_introduced)",
        "Switch expression conversions (diff markers): $($gitMetrics.switch_expression_or_pattern_switch_conversions)",
        "Virtual thread points introduced (diff markers): $($gitMetrics.virtual_thread_usage_points_introduced)",
        "",
        "Quality and validation",
        "Static analysis issues before: $($baselineSpotbugs.total_bug_instances)",
        "Static analysis issues after: $($spotbugsSummary.total_bug_instances)",
        "Blocking findings before (static): $($baselineSpotbugs.blocking_bug_instances)",
        "Blocking findings after (static): $($spotbugsSummary.blocking_bug_instances)",
        "Blocking findings after (runtime JFR): $($jfrMetrics.runtime_blocking_event_count)",
        "Test pass rate before: $($baselineTests.pass_rate_pct)%",
        "Test pass rate after: $($currentTests.pass_rate_pct)%",
        "Coverage before: $($baselineManifest.coverage.line_coverage_pct)%",
        "Coverage after: $jacocoLineCoverageAfter%",
        "Introduced failing tests: $(if ($introducedFailures.Count -gt 0) { $introducedFailures -join ', ' } else { 'none' })",
        "Resolved failing tests: $(if ($resolvedFailures.Count -gt 0) { $resolvedFailures -join ', ' } else { 'none' })",
        "",
        "Runtime benchmark",
        "Cold startup: $($coldStartup.startup_ms) ms",
        "Warm startup: $($warmStartup.startup_ms) ms",
        "100 users: TPS=$($migrationMetrics.runtime_benchmark.load_test.users_100.throughput_rps) P95=$($migrationMetrics.runtime_benchmark.load_test.users_100.latency_ms.p95)ms P99=$($migrationMetrics.runtime_benchmark.load_test.users_100.latency_ms.p99)ms Err=$($migrationMetrics.runtime_benchmark.load_test.users_100.error_rate_pct)%",
        "250 users: TPS=$($migrationMetrics.runtime_benchmark.load_test.users_250.throughput_rps) P95=$($migrationMetrics.runtime_benchmark.load_test.users_250.latency_ms.p95)ms P99=$($migrationMetrics.runtime_benchmark.load_test.users_250.latency_ms.p99)ms Err=$($migrationMetrics.runtime_benchmark.load_test.users_250.error_rate_pct)%",
        "500 users: TPS=$($migrationMetrics.runtime_benchmark.load_test.users_500.throughput_rps) P95=$($migrationMetrics.runtime_benchmark.load_test.users_500.latency_ms.p95)ms P99=$($migrationMetrics.runtime_benchmark.load_test.users_500.latency_ms.p99)ms Err=$($migrationMetrics.runtime_benchmark.load_test.users_500.error_rate_pct)%",
        "",
        "Summary JSON: $summaryPath",
        "Detailed metrics JSON: $metricsReportPath"
    ) | Set-Content -Path $reportPath

    Write-Host "Migration benchmark completed. Summary: $summaryPath"
}
finally {
    Stop-Port8080Processes
    Pop-Location
}
