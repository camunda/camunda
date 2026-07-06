/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * CANARY — DO NOT MERGE.
 *
 * <p>Temporary flaky test that validates the flaky-gate sticky-state persistence fix (commit "fix:
 * persist flaky-gate sticky state across CI runs"). Exists only on the throwaway branch {@code
 * ci/flaky-gate-state-canary-DONOTMERGE}; remove before that branch is ever merged.
 *
 * <p>It lives in {@code zeebe-util} so it runs in the gate-collected {@code zeebe-unit-tests} job.
 * The test file is in the PR diff, so the gate's touch-check keeps the alert (test-file-in-diff
 * override).
 *
 * <p><b>Deterministic flake:</b> the first execution creates a marker file and fails; Maven's
 * retry (same runner filesystem) sees the marker and passes. Net result on every CI run: failed
 * once, passed on retry → recorded in FLAKY.xml → the gate flags it. This removes the ~50% luck of
 * a random flake so the sticky-state / counter path is exercised reliably.
 *
 * <p><b>Phase 1 (flag):</b> as above → gate raises a sticky alert and (with the fix) uploads the
 * {@code flaky-gate-state-pr-<N>} artifact.
 *
 * <p><b>Phase 2 (clear):</b> replace the body of {@link #shouldBeStable()} with a no-op (delete the
 * fail-then-pass logic). That stops the flake AND counts as a method fix, so the next three clean
 * re-runs advance the counter 1/3 → 2/3 → 3/3 → {@code cleared_via_fix}. Assert run #2's log shows
 * "Found prior artifact id: …" (not "No prior sticky-state artifact found").
 */
final class FlakyGateCanaryTest {

  @Test
  void shouldBeStable() throws IOException {
    // PHASE 1 (flag): fail on the first attempt, pass on Maven's retry -> recorded flaky.
    // PHASE 2 (clear): replace this whole body with a no-op (e.g. `// cleared`).
    final Path marker =
        Path.of(System.getProperty("java.io.tmpdir"), "flaky-gate-canary-attempt.marker");
    if (Files.exists(marker)) {
      return; // retry attempt on the same runner: pass
    }
    Files.createFile(marker);
    throw new AssertionError(
        "flaky-gate canary: intentional first-attempt failure to exercise the flaky gate");
  }
}
