/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.job.JobSecretInjector.FailedInjectionJob;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup.SecretPointerMismatchException;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.io.IOException;
import org.junit.jupiter.api.Test;

final class JobSecretInjectionIncidentTest {

  private static final long JOB_KEY = 42L;
  private static final String PATH = "/authToken";
  private static final String PLACEHOLDER = "camunda.secrets.tokenB";

  /** A job the batch path dropped, in the shape {@code JobSecretInjector} hands it over. */
  private static FailedInjectionJob droppedJob(final String path, final String placeholder) {
    return new FailedInjectionJob(JOB_KEY, new JobRecord(), path, placeholder);
  }

  @Test
  void shouldNameTheMismatchedReferenceOfAThrownMismatch() {
    // given - the job-push path catches the mismatch as it is thrown
    final var failure = new SecretPointerMismatchException(PATH, PLACEHOLDER);

    // when
    final var message = JobSecretInjectionIncident.messageFor(JOB_KEY, failure);

    // then - the incident names the reference and where it sits, so an operator can fix the
    // input mapping that produced it
    assertThat(message)
        .contains(String.valueOf(JOB_KEY))
        .contains(
            "the secret reference '%s' could not be resolved at '%s'".formatted(PLACEHOLDER, PATH));
  }

  @Test
  void shouldFallBackToCauseNeutralMessageWhenFailureIsNotAMismatch() {
    // given - an injection failure the path cannot attribute to a specific reference, e.g. the
    // job's variables are not valid msgpack, which surfaces as an IOException rather than a
    // SecretPointerMismatchException
    final var failure = new IOException("variables document quoting a secret value");

    // when
    final var message = JobSecretInjectionIncident.messageFor(JOB_KEY, failure);

    // then - the incident carries the cause-neutral fallback, naming neither a reference nor the
    // exception's own message, which may quote the variables document and with it secret data
    assertThat(message)
        .contains(String.valueOf(JOB_KEY))
        .contains("injecting its secret values failed")
        .doesNotContain("could not be resolved at")
        .doesNotContain(failure.getMessage());
  }

  @Test
  void shouldNameTheMismatchedReferenceReadOutOfTheDroppedJob() {
    // given - the batch path has already read the mismatch off the dropped job
    final var dropped = droppedJob(PATH, PLACEHOLDER);

    // when
    final var message = JobSecretInjectionIncident.messageFor(dropped);

    // then - the placeholder and the JSON pointer land the right way round, so the operator is
    // not sent to a variable path that does not exist
    assertThat(message)
        .contains(String.valueOf(JOB_KEY))
        .contains(
            "the secret reference '%s' could not be resolved at '%s'".formatted(PLACEHOLDER, PATH));
  }

  @Test
  void shouldFallBackToCauseNeutralMessageWhenNoReferenceWasReadOut() {
    // given - the batch path drops a job whose failure identified no reference, so it carries
    // neither a placeholder nor a path
    final var dropped = droppedJob(null, null);

    // when
    final var message = JobSecretInjectionIncident.messageFor(dropped);

    // then
    assertThat(message)
        .contains(String.valueOf(JOB_KEY))
        .contains("injecting its secret values failed")
        .doesNotContain("could not be resolved at");
  }

  @Test
  void shouldFallBackToCauseNeutralMessageWhenOnlyHalfOfTheReferenceIsKnown() {
    // given - a dropped job carrying a path but no placeholder, which no failure produces today,
    // but which a later one could
    final var dropped = droppedJob(PATH, null);

    // when
    final var message = JobSecretInjectionIncident.messageFor(dropped);

    // then - half a reference names no reference at all: it would point an operator at a variable
    // path with no placeholder to fix, or the other way round
    assertThat(message)
        .contains("injecting its secret values failed")
        .doesNotContain("could not be resolved at")
        .doesNotContain("null");
  }

  @Test
  void shouldGiveBothActivationPathsTheSameMessageForTheSameFailure() {
    // given - the same mismatch, reached as the job-push path sees it (thrown) and as the batch
    // path sees it (already read out onto the dropped job)
    final var thrown = new SecretPointerMismatchException(PATH, PLACEHOLDER);
    final var dropped = droppedJob(PATH, PLACEHOLDER);

    // when
    final var pushMessage = JobSecretInjectionIncident.messageFor(JOB_KEY, thrown);
    final var batchMessage = JobSecretInjectionIncident.messageFor(dropped);

    // then - which activation path served the job is an implementation detail the operator must
    // not be able to tell from the incident
    assertThat(pushMessage).isEqualTo(batchMessage);
  }

  @Test
  void shouldGiveBothActivationPathsTheSameCauseNeutralMessage() {
    // given - a failure identifying no reference, on either path
    final var thrown = new IOException("not valid msgpack");
    final var dropped = droppedJob(null, null);

    // when
    final var pushMessage = JobSecretInjectionIncident.messageFor(JOB_KEY, thrown);
    final var batchMessage = JobSecretInjectionIncident.messageFor(dropped);

    // then
    assertThat(pushMessage).isEqualTo(batchMessage);
  }
}
