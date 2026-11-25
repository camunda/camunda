/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.es9;

/**
 * The exit-code contract between the Job and the operator's Job definition. Any other exit code
 * (e.g. {@link #UNEXPECTED_ERROR}) is a crash that Kubernetes retries.
 */
public enum ExitCode {
  DONE(0),
  NEEDS_HUMAN_CLEAN(10),
  NEEDS_HUMAN_UNCLEAN(20);

  /** Outside the contract on purpose, so that Kubernetes retries the pod. */
  public static final int UNEXPECTED_ERROR = 1;

  private final int code;

  ExitCode(final int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }

  /** The only mapping from a mode's result to its exit code. */
  public static ExitCode of(final ModeResult result) {
    return switch (result) {
      case ModeResult.Done ignored -> DONE;
      case ModeResult.NeedsHumanClean ignored -> NEEDS_HUMAN_CLEAN;
      case ModeResult.NeedsHumanUnclean ignored -> NEEDS_HUMAN_UNCLEAN;
    };
  }
}
