package org.sasanlabs.benchmark.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sasanlabs.benchmark.model.BenchmarkResult;
import org.sasanlabs.benchmark.model.ScannerFindings;
import org.sasanlabs.benchmark.service.BenchmarkResultWriter;
import org.sasanlabs.benchmark.service.BenchmarkService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST entry point for benchmarking a scanner's findings against VulnerableApp's DAST ground truth.
 * Mounted alongside the existing {@code /scanner} and {@code /scanner/metadata} endpoints.
 */
@Profile("unsafe")
@RestController
public class BenchmarkController {

    private static final Logger LOGGER = LogManager.getLogger(BenchmarkController.class);

    private final BenchmarkService benchmarkService;
    private final BenchmarkResultWriter benchmarkResultWriter;
    private final String apiKey;

    public BenchmarkController(
            BenchmarkService benchmarkService,
            BenchmarkResultWriter benchmarkResultWriter,
            @Value("${benchmark.api-key}") String apiKey) {
        this.benchmarkService = benchmarkService;
        this.benchmarkResultWriter = benchmarkResultWriter;
        this.apiKey = apiKey;
    }

    @PostMapping("/scanner/benchmark")
    public ResponseEntity<?> benchmark(
            @RequestHeader(value = "X-Benchmark-Api-Key", required = false) String providedKey,
            @RequestBody ScannerFindings input)
            throws IOException {
        if (!isValidApiKey(providedKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid API key"));
        }
        if (input == null || input.getTool() == null || input.getTool().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Field 'tool' is required and must be non-empty"));
        }
        if (input.getFindings() == null) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    "Field 'findings' is required (use [] for an empty list)"));
        }

        BenchmarkResult result = benchmarkService.compare(input);

        try {
            Path written = benchmarkResultWriter.write(result);
            LOGGER.info("Wrote benchmark result for tool '{}' to {}", input.getTool(), written);
        } catch (IOException ioe) {
            LOGGER.error(
                    "Failed to persist benchmark result for tool '{}'; returning 500 with result"
                            + " in body",
                    input.getTool(),
                    ioe);
            result.setPersistenceError("Failed to persist benchmark result: " + ioe.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }

        return ResponseEntity.ok(result);
    }

    private boolean isValidApiKey(String providedKey) {
        if (providedKey == null || apiKey == null || apiKey.isBlank()) {
            return false;
        }
        byte[] expected = apiKey.getBytes(StandardCharsets.UTF_8);
        byte[] actual = providedKey.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
