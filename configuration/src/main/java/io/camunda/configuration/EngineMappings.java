/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import org.jspecify.annotations.Nullable;

/**
 * Defines configuration for the engine's mapping resolution strategy. The prefix for this class is
 * camunda.processing.engine.mappings.
 */
public class EngineMappings {

  private InputMode inputMode = InputMode.COMBINED;
  private OutputMode outputMode = OutputMode.COMBINED;

  /**
   * When set, evaluates input mappings with both the primary resolver ({@code inputMode}) and this
   * comparison resolver, then logs a warning if their results differ. Intended for diagnostic use
   * only: each activation evaluates mappings with both resolvers and compares the result documents,
   * which adds overhead for large mapping sets.
   *
   * <p>Has no effect when null (default) or when set to the same value as {@code inputMode}.
   */
  @Nullable private InputMode inputComparisonMode = null;

  public enum InputMode {
    ORDERED,
    COMBINED
  }

  public enum OutputMode {
    ORDERED,
    COMBINED
  }

  /**
   * Controls the input-mapping resolver used during process instance execution. When set to {@code
   * COMBINED}, the engine uses {@code CombinedInputMappingResolver} which merges all input mappings into
   * a single combined result. When set to {@code ORDERED}, the engine uses {@code
   * OrderedMappingResolver} which applies mappings in modeling order.
   *
   * <p>This configuration can be accessed via the environment variable: <br>
   * {@code camunda.processing.engine.mappings.input-mode}.
   *
   * <p>Defaults to {@code COMBINED}.
   */
  public InputMode getInputMode() {
    return inputMode;
  }

  public void setInputMode(final InputMode inputMode) {
    this.inputMode = inputMode;
  }

  public @Nullable InputMode getInputComparisonMode() {
    return inputComparisonMode;
  }

  public void setInputComparisonMode(final @Nullable InputMode inputComparisonMode) {
    this.inputComparisonMode = inputComparisonMode;
  }

  public OutputMode getOutputMode() {
    return outputMode;
  }

  public void setOutputMode(final OutputMode outputMode) {
    this.outputMode = outputMode;
  }

  @Override
  public String toString() {
    return "EngineMappings{inputMode="
        + inputMode
        + ", inputComparisonMode="
        + inputComparisonMode
        + ", outputMode="
        + outputMode
        + '}';
  }
}
