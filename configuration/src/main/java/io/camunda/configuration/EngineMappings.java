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
 *
 * <p><strong>Experimental:</strong> This configuration and all input/output mapping modes are
 * experimental and may change in a future release.
 */
public class EngineMappings {

  private InputMode inputMode = InputMode.COMBINED;
  private OutputMode outputMode = OutputMode.COMBINED;

  @Nullable private InputMode inputComparisonMode = null;

  @Nullable private OutputMode outputComparisonMode = null;

  /**
   * Experimental input-mapping resolution modes.
   *
   * <p><strong>Experimental:</strong> These modes may change in a future release.
   */
  public enum InputMode {
    ORDERED,
    COMBINED
  }

  /**
   * Experimental output-mapping resolution modes.
   *
   * <p><strong>Experimental:</strong> These modes may change in a future release.
   */
  public enum OutputMode {
    ORDERED,
    COMBINED
  }

  /**
   * Controls the input-mapping resolver used during process instance execution. When set to {@code
   * COMBINED}, all input mappings are merged into a single combined result. When set to {@code
   * ORDERED}, mappings are applied in modeling order.
   *
   * <p><strong>Experimental:</strong> This setting and all input modes are experimental and may
   * change in a future release.
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

  /**
   * Controls the optional comparison mode for input mappings. When set, input mappings are evaluated
   * with both the primary mode ({@code inputMode}) and this comparison mode, and a warning is
   * logged if the results differ.
   *
   * <p><strong>Experimental:</strong> This setting and all input modes are experimental and may
   * change in a future release.
   *
   * <p>Intended for diagnostic use only: each activation evaluates mappings with both modes and
   * compares the result documents, which adds overhead for large mapping sets.
   *
   * <p>This configuration can be accessed via the environment variable: <br>
   * {@code camunda.processing.engine.mappings.input-comparison-mode}.
   *
   * <p>Defaults to {@code null}. Has no effect when null or when set to the same value as
   * {@code input-mode}.
   */
  public @Nullable InputMode getInputComparisonMode() {
    return inputComparisonMode;
  }

  public void setInputComparisonMode(final @Nullable InputMode inputComparisonMode) {
    this.inputComparisonMode = inputComparisonMode;
  }

  /**
   * Controls the output-mapping resolver used during process instance execution. When set to {@code
   * COMBINED}, all output mappings are merged into a single combined result. When set to {@code
   * ORDERED}, mappings are applied in modeling order.
   *
   * <p><strong>Experimental:</strong> This setting and all output modes are experimental and may
   * change in a future release.
   *
   * <p>This configuration can be accessed via the environment variable: <br>
   * {@code camunda.processing.engine.mappings.output-mode}.
   *
   * <p>Defaults to {@code COMBINED}.
   */
  public OutputMode getOutputMode() {
    return outputMode;
  }

  public void setOutputMode(final OutputMode outputMode) {
    this.outputMode = outputMode;
  }

  /**
   * Controls the optional comparison mode for output mappings. When set, output mappings are
   * evaluated with both the primary mode ({@code outputMode}) and this comparison mode, and a
   * warning is logged if the results differ.
   *
   * <p><strong>Experimental:</strong> This setting and all output modes are experimental and may
   * change in a future release.
   *
   * <p>Intended for diagnostic use only: each completion evaluates mappings with both modes and
   * compares the result documents, which adds overhead for large mapping sets.
   *
   * <p>This configuration can be accessed via the environment variable: <br>
   * {@code camunda.processing.engine.mappings.output-comparison-mode}.
   *
   * <p>Defaults to {@code null}. Has no effect when null or when set to the same value as
   * {@code output-mode}.
   */
  public @Nullable OutputMode getOutputComparisonMode() {
    return outputComparisonMode;
  }

  public void setOutputComparisonMode(final @Nullable OutputMode outputComparisonMode) {
    this.outputComparisonMode = outputComparisonMode;
  }

  @Override
  public String toString() {
    return "EngineMappings{inputMode="
        + inputMode
        + ", inputComparisonMode="
        + inputComparisonMode
        + ", outputMode="
        + outputMode
        + ", outputComparisonMode="
        + outputComparisonMode
        + '}';
  }
}
