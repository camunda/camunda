/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.job.JobSecretInjector.FailedInjectionJob;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup.SecretPointerMismatchException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The message of the {@code SECRET_RESOLUTION_ERROR} incident raised for a job whose secret value
 * injection failed, shared by both activation paths: the long-poll batch path ({@link
 * JobBatchActivateProcessor}) and the job-push path ({@code BpmnJobActivationBehavior}). Both
 * wordings and the choice between them live here only, so editing one path cannot leave the other
 * behind.
 *
 * <p>Each path passes the failure in the shape it holds it - thrown, or already read out onto a
 * {@link FailedInjectionJob} - rather than the placeholder and the path as two loose strings, so a
 * caller cannot transpose them into a message that sends an operator to a variable path that does
 * not exist.
 *
 * <p>Only the mismatched reference's placeholder and JSON pointer are ever named, never the
 * secret's value and never the failure's own message, which may quote the variables document and
 * with it secret data that must stay out of persisted records. A failure that identifies no
 * reference (e.g. the job's variables are not valid msgpack) gets a cause-neutral message rather
 * than one naming a mismatch that was never established.
 */
@NullMarked
public final class JobSecretInjectionIncident {

  private static final String SECRET_INJECTION_UNKNOWN_CAUSE_MESSAGE =
      "The job with key '%s' can not be activated, because injecting its secret values "
          + "failed. Resolve the incident, or use process instance modification to "
          + "reactivate the element and create a fresh job.";

  private static final String SECRET_REFERENCE_UNRESOLVED_MESSAGE =
      "The job with key '%s' can not be activated, because the secret reference '%s' "
          + "could not be resolved at '%s'. Fix the variable's value or the input "
          + "mapping that sets it, then resolve the incident, or use process instance "
          + "modification to reactivate the element and create a fresh job.";

  private JobSecretInjectionIncident() {}

  /**
   * The message for a failure caught as it was thrown, as the job-push path has it. Reads the
   * mismatched reference off a {@link SecretPointerMismatchException} and nothing at all off any
   * other failure - in particular not its message.
   */
  public static String messageFor(final long jobKey, final Throwable failure) {
    return failure instanceof final SecretPointerMismatchException mismatch
        ? messageFor(jobKey, mismatch.placeholder(), mismatch.path())
        : messageFor(jobKey, null, null);
  }

  /**
   * The message for a job the batch path dropped, whose mismatched reference - if the failure
   * identified one - the drop already read out onto the job.
   */
  public static String messageFor(final FailedInjectionJob failed) {
    return messageFor(failed.jobKey(), failed.placeholder(), failed.path());
  }

  /**
   * A reference is only named when both halves of it are known: half of one points an operator at a
   * placeholder that sits nowhere, or at a variable path that holds nothing, so such a failure gets
   * the cause-neutral message instead.
   */
  private static String messageFor(
      final long jobKey, final @Nullable String placeholder, final @Nullable String path) {
    return placeholder == null || path == null
        ? SECRET_INJECTION_UNKNOWN_CAUSE_MESSAGE.formatted(jobKey)
        : SECRET_REFERENCE_UNRESOLVED_MESSAGE.formatted(jobKey, placeholder, path);
  }
}
