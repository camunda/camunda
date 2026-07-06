/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

/**
 * CANARY — DO NOT MERGE.
 *
 * <p>Temporary flaky test that validates the flaky-gate sticky-state persistence fix (commit "fix:
 * persist flaky-gate sticky state across CI runs"). It exists only on the throwaway branch {@code
 * test/flaky-gate-state-canary} and must be removed before that branch — if ever — is merged.
 *
 * <p>It lives in the {@code zeebe-util} module so it runs in the {@code zeebe-unit-tests} job, which
 * the flaky gate collects. Because the test file itself is in the PR diff, the gate's touch-check
 * keeps the alert (test-file-in-diff override).
 *
 * <p>Validation walkthrough:
 *
 * <ul>
 *   <li><b>Phase 1 (flag):</b> {@link #shouldBeStable()} fails ~50% of the time, so Maven's retry
 *       records it in FLAKY.xml and the gate raises a sticky alert, uploading the {@code
 *       flaky-gate-state-pr-<N>} artifact. Assert: the artifact now exists and the "Upload
 *       sticky-state artifact" step runs (before the fix it was skipped).
 *   <li><b>Phase 2 (clear):</b> replace the body of {@link #shouldBeStable()} with {@code
 *       assertThat(true).isTrue();}. That stops the flake AND counts as a method fix, so the next
 *       three clean re-runs advance the counter 1/3 → 2/3 → 3/3 → {@code cleared_via_fix}. Assert:
 *       run #2's log reports "Found prior artifact id: …" (not "No prior sticky-state artifact
 *       found"), proving state now persists across runs.
 * </ul>
 */
final class FlakyGateCanaryTest {

  @Test
  void shouldBeStable() {
    // PHASE 1 (flag): ~50% failure so the test is recorded as flaky.
    // PHASE 2 (clear): replace the assertion below with `assertThat(true).isTrue();`.
    assertThat(ThreadLocalRandom.current().nextInt(2)).isZero();
  }
}
