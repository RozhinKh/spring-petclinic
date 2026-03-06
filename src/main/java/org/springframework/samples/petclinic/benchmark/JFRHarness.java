/*
 * Copyright 2015-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.benchmark;

import jdk.jfr.Configuration;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;

/**
 * Manages Java Flight Recorder (JFR) recording lifecycle for benchmark execution. Enables
 * programmatic start/stop of JFR recording with configured event types, and saves
 * recordings to disk for later analysis and correlation.
 */
public class JFRHarness {

	private Recording recording;

	private Path recordingPath;

	private long recordingStartTimeMs;

	private long recordingStartTimeNs;

	private long recordingEndTimeMs;

	private long recordingEndTimeNs;

	/**
	 * Start a JFR recording with standard benchmark event configuration. Records GC,
	 * threading, memory allocation, and blocking events.
	 * @throws Exception if recording cannot be started
	 */
	public void startRecording() throws Exception {
		if (recording != null) {
			stopRecording();
		}

		// Create output directory if needed
		Path outputDir = Paths.get("jfr-recordings");
		Files.createDirectories(outputDir);

		// Create recording with configuration
		recording = new Recording();
		recording.setName("benchmark-recording-" + System.currentTimeMillis());

		// Configure events to capture
		configureEvents();

		// Start recording
		recordingStartTimeMs = System.currentTimeMillis();
		recordingStartTimeNs = System.nanoTime();
		recording.start();

		System.out.println("✓ JFR recording started");
	}

	/**
	 * Stop the current JFR recording and save to disk.
	 * @return Path to the saved JFR recording file
	 * @throws Exception if recording cannot be stopped or saved
	 */
	public Path stopRecording() throws Exception {
		if (recording == null) {
			throw new IllegalStateException("No recording is currently active");
		}

		recordingEndTimeMs = System.currentTimeMillis();
		recordingEndTimeNs = System.nanoTime();

		// Stop recording
		recording.stop();

		// Save to file
		Path outputDir = Paths.get("jfr-recordings");
		Files.createDirectories(outputDir);

		recordingPath = outputDir.resolve("benchmark-" + System.currentTimeMillis() + ".jfr");
		recording.dump(recordingPath);

		System.out.println("✓ JFR recording saved to: " + recordingPath);

		recording.close();
		recording = null;

		return recordingPath;
	}

	/**
	 * Configure JFR events to capture during recording. Enables key event types for GC,
	 * threading, memory, and blocking analysis.
	 */
	private void configureEvents() {
		// Enable GC events
		recording.enable("jdk.GarbageCollection").withStackTrace();
		recording.enable("jdk.GCPauseLevel").withStackTrace();
		recording.enable("jdk.GCHeapSummary");
		recording.enable("jdk.GCHeapMemoryUsage");
		recording.enable("jdk.GCAllocationRequiringGC");

		// Enable thread events
		recording.enable("jdk.ThreadStart");
		recording.enable("jdk.ThreadEnd");
		recording.enable("jdk.ThreadSleep");

		// Enable monitor/lock events
		recording.enable("jdk.JavaMonitorEnter").withStackTrace().withThreshold(Duration.ZERO);
		recording.enable("jdk.JavaMonitorWait").withStackTrace().withThreshold(Duration.ZERO);

		// Enable memory allocation events
		recording.enable("jdk.ObjectAllocationInNewTLAB");
		recording.enable("jdk.ObjectAllocationOutsideTLAB");

		// Enable thread park events (relevant for virtual threads)
		recording.enable("jdk.ThreadPark").withStackTrace();
		recording.enable("jdk.Upcall");

		// Enable method profiling for context
		recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));

		System.out.println("✓ JFR events configured");
	}

	/**
	 * Get the path to the saved JFR recording file.
	 */
	public Path getRecordingPath() {
		return recordingPath;
	}

	/**
	 * Get recording start time in milliseconds (system time).
	 */
	public long getRecordingStartTimeMs() {
		return recordingStartTimeMs;
	}

	/**
	 * Get recording start time in nanoseconds (for correlation with JFR events).
	 */
	public long getRecordingStartTimeNs() {
		return recordingStartTimeNs;
	}

	/**
	 * Get recording end time in milliseconds (system time).
	 */
	public long getRecordingEndTimeMs() {
		return recordingEndTimeMs;
	}

	/**
	 * Get recording end time in nanoseconds (for correlation with JFR events).
	 */
	public long getRecordingEndTimeNs() {
		return recordingEndTimeNs;
	}

	/**
	 * Get recording duration in milliseconds.
	 */
	public long getRecordingDurationMs() {
		if (recordingEndTimeMs == 0) {
			return System.currentTimeMillis() - recordingStartTimeMs;
		}
		return recordingEndTimeMs - recordingStartTimeMs;
	}

	/**
	 * Check if a recording is currently active.
	 */
	public boolean isRecording() {
		return recording != null && recording.getState() == RecordingState.RUNNING;
	}

}
