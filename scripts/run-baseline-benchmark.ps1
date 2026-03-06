param(
    [string]$Variant = "java17-baseline",
    [int]$LoadTestDurationSeconds = 30
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$resultsDir = Join-Path $projectRoot "benchmark-results\$timestamp"
$jarPath = Join-Path $projectRoot "target\spring-petclinic-4.0.0-SNAPSHOT.jar"
$loadResultsPath = Join-Path $resultsDir "load-test-results.json"
$metricsPath = Join-Path $resultsDir "metrics.jsonl"
$jfrPath = Join-Path $resultsDir "recording.jfr"
$jfrJsonPath = Join-Path $resultsDir "recording.json"
$jmhResultsPath = Join-Path $resultsDir "jmh-results.json"
$spotbugsSummaryPath = Join-Path $resultsDir "spotbugs-blocking-summary.json"
$summaryPath = Join-Path $resultsDir "benchmark-summary.json"
$reportPath = Join-Path $resultsDir "RESULTS.txt"

New-Item -ItemType Directory -Path $resultsDir -Force | Out-Null

function Stop-Port8080Processes {
    Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique |
        ForEach-Object {
            Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
        }
    Start-Sleep -Seconds 2
}

function Wait-ForHealth([int]$TimeoutSeconds = 60) {
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

function Start-App([string]$logPath, [string[]]$extraJvmArgs = @(), [string[]]$extraAppArgs = @()) {
    Stop-Port8080Processes
    $quotedJarPath = '"' + $jarPath + '"'
    $argList = @("-Xms256m", "-Xmx512m") + $extraJvmArgs + @("-jar", $quotedJarPath, "--spring.profiles.active=benchmark") + $extraAppArgs
    $stdoutPath = $logPath
    $stderrPath = [System.IO.Path]::ChangeExtension($logPath, ".err.log")
    $process = Start-Process -FilePath "java" -ArgumentList $argList -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -PassThru -WindowStyle Hidden
    if (-not (Wait-ForHealth 90)) {
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

function Get-ModernizationMetrics {
    $javaFiles = Get-ChildItem -Path (Join-Path $projectRoot "src\main\java") -Recurse -Filter *.java
    $totalLoc = 0
    $recordFiles = 0
    $patternMatchingUsage = 0
    $switchPatternUsage = 0
    $virtualThreadUsage = 0
    foreach ($file in $javaFiles) {
        $content = Get-Content $file.FullName
        $totalLoc += $content.Count
        if ($content -match '^\s*public\s+record\s+|^\s*record\s+') {
            $recordFiles++
        }
        $patternMatchingUsage += @($content | Select-String -Pattern 'instanceof\s+\w+\s+\w+').Count
        $switchPatternUsage += @($content | Select-String -Pattern '\bwhen\b|\byield\b').Count
        $virtualThreadUsage += @($content | Select-String -Pattern 'VirtualThread|newVirtualThread|virtualThread').Count
    }

    $modernizationReportPath = Join-Path $projectRoot "modernization-metrics.json"
    $locRefactored = $null
    if (Test-Path $modernizationReportPath) {
        $modernizationReport = Get-Content $modernizationReportPath -Raw | ConvertFrom-Json
        $locRefactored = $modernizationReport.summary.baselineComparison.java21VariantA.locSavings
    }

    [ordered]@{
        total_java_loc = $totalLoc
        loc_refactored = $locRefactored
        records_used = $recordFiles
        pattern_matching_usage = $patternMatchingUsage
        switch_pattern_usage = $switchPatternUsage
        virtual_thread_usage_points = $virtualThreadUsage
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
            runtime_blocking_events = $blockingEvents.Count
            gc_pause_total_ms = [math]::Round($gcPauseMs, 2)
            sampled_allocation_events = $allocationEvents.Count
        }
    }
    catch {
        $blockingCount = (jfr print --events jdk.JavaMonitorWait,jdk.ThreadPark $jfrPath | Select-String -Pattern '^jdk\.(JavaMonitorWait|ThreadPark) \{').Count
        return [ordered]@{
            runtime_blocking_event_count = $blockingCount
            runtime_blocking_events = $blockingCount
        }
    }
}

function Parse-SpotBugsSummary {
    $spotbugsCandidates = @(
        (Join-Path $projectRoot "target\spotbugsXml.xml"),
        (Join-Path $projectRoot "target\spotbugs\spotbugs.xml")
    )
    $spotbugsXml = $spotbugsCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not (Test-Path $spotbugsXml)) {
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

Push-Location $projectRoot
try {
    Write-Host "Building baseline package..."
    & "$projectRoot\mvnw.cmd" -q clean package "-Dmaven.test.skip=true" "-Dcheckstyle.skip=true" "-Dnohttp.skip=true" "-Dspring-javaformat.skip=true"

    if (-not (Test-Path $jarPath)) {
        throw "Built jar not found at $jarPath"
    }

    $modernization = Get-ModernizationMetrics
    $coldStartup = Measure-Startup -label "cold" -logPath (Join-Path $resultsDir "cold-start.log")
    $warmStartup = Measure-Startup -label "warm" -logPath (Join-Path $resultsDir "warm-start.log")

    Write-Host "Starting application with JFR..."
    $jfrRecordingArg = '"-XX:StartFlightRecording=settings=profile,maxsize=256m,dumponexit=true,filename=' + $jfrPath + '"'
    $appProcess = Start-App -logPath (Join-Path $resultsDir "app.log") -extraJvmArgs @($jfrRecordingArg)
    try {
        Write-Host "Running load test..."
        $loadTestOut = Join-Path $resultsDir "load-test.log"
        $loadTestErr = Join-Path $resultsDir "load-test.err.log"
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
        Get-Content $loadTestOut

        Write-Host "Running JMH latency and throughput benchmarks..."
        $jmhResults = Run-JmhBenchmarks
    }
    finally {
        Stop-App $appProcess $jfrPath
    }

    Write-Host "Running SpotBugs..."
    & "$projectRoot\mvnw.cmd" -q "-Dmaven.test.skip=true" "-Dcheckstyle.skip=true" "-Dnohttp.skip=true" "-Dspring-javaformat.skip=true" "com.github.spotbugs:spotbugs-maven-plugin:4.8.2.0:spotbugs"

    $loadResults = Get-Content $loadResultsPath -Raw | ConvertFrom-Json
    $jmhSummary = Summarize-JmhResults $jmhResults
    $jfrMetrics = Parse-JfrMetrics
    $spotbugsSummary = Parse-SpotBugsSummary
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

    $summary = [ordered]@{
        variant = $Variant
        timestamp = (Get-Date).ToUniversalTime().ToString("o")
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
        modernization = $modernization
    }

    $summary | ConvertTo-Json -Depth 8 | Set-Content -Path $summaryPath

    @(
        "BENCHMARK RESULTS - $Variant"
        "Results directory: $resultsDir"
        ""
        "Cold startup: $($coldStartup.startup_ms) ms"
        "Warm startup: $($warmStartup.startup_ms) ms"
        ""
        "Load test:"
        "100 users: TPS=$($summary.load_test.users_100.throughput_rps) P50=$($summary.load_test.users_100.latency_ms.p50)ms P95=$($summary.load_test.users_100.latency_ms.p95)ms P99=$($summary.load_test.users_100.latency_ms.p99)ms P99.9=$($summary.load_test.users_100.latency_ms.p99_9)ms Err=$($summary.load_test.users_100.error_rate_pct)%"
        "250 users: TPS=$($summary.load_test.users_250.throughput_rps) P50=$($summary.load_test.users_250.latency_ms.p50)ms P95=$($summary.load_test.users_250.latency_ms.p95)ms P99=$($summary.load_test.users_250.latency_ms.p99)ms P99.9=$($summary.load_test.users_250.latency_ms.p99_9)ms Err=$($summary.load_test.users_250.error_rate_pct)%"
        "500 users: TPS=$($summary.load_test.users_500.throughput_rps) P50=$($summary.load_test.users_500.latency_ms.p50)ms P95=$($summary.load_test.users_500.latency_ms.p95)ms P99=$($summary.load_test.users_500.latency_ms.p99)ms P99.9=$($summary.load_test.users_500.latency_ms.p99_9)ms Err=$($summary.load_test.users_500.error_rate_pct)%"
        ""
        "Allocation rate: $allocationRateMbPerSec MB/s"
        "Promotion rate: $promotionRateMbPerSec MB/s"
        "SpotBugs blocking findings: $($summary.spotbugs.blocking_bug_instances)"
        "Runtime blocking events from JFR: $($summary.jfr.runtime_blocking_event_count)"
    ) | Set-Content -Path $reportPath

    Write-Host "Benchmark completed. Summary: $summaryPath"
}
finally {
    Stop-Port8080Processes
    Pop-Location
}
