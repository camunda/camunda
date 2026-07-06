/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.core.ResolvableType;

public class LoopDetection {

  private static final String PREFIX = "camunda.processing.engine.loop-detection";

  // Must stay in sync with EngineConfiguration.DEFAULT_MAX_ELEMENT_ACTIVATION_COUNT.
  private static final int DEFAULT_MAX_ELEMENT_ACTIVATION_COUNT = 1000;
  // Must stay in sync with EngineConfiguration.DEFAULT_ELEMENT_ACTIVATION_RETRY_COOLDOWN.
  private static final int DEFAULT_ELEMENT_ACTIVATION_RETRY_COOLDOWN = 100;

  private static final Set<String> LEGACY_MAX_ELEMENT_ACTIVATION_COUNT_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.loopDetection.maxElementActivationCount");
  private static final Set<String> LEGACY_ELEMENT_ACTIVATION_RETRY_COOLDOWN_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.loopDetection.elementActivationRetryCooldown");
  private static final Set<String> LEGACY_MAX_ELEMENT_ACTIVATION_COUNT_BY_TYPE_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.loopDetection.maxElementActivationCountByType");

  /**
   * The maximum number of times a single BPMN element may be activated within one process instance
   * before a loop-detection incident is raised. Used as the default for element types without a
   * per-type override in {@link #maxElementActivationCountByType}.
   */
  private int maxElementActivationCount = DEFAULT_MAX_ELEMENT_ACTIVATION_COUNT;

  /**
   * Once the threshold is first exceeded, the loop-detection incident is re-raised every {@code
   * elementActivationRetryCooldown} further activations. This gives operators a window to resolve
   * the incident before a new one is raised. A value of {@code 1} (or less) disables throttling, so
   * the incident is re-raised on every activation beyond the threshold.
   */
  private int elementActivationRetryCooldown = DEFAULT_ELEMENT_ACTIVATION_RETRY_COOLDOWN;

  /**
   * Per-{@link BpmnElementType} overrides for {@link #maxElementActivationCount}. An element type
   * present in this map uses the mapped value instead of the global default; a value of {@code 0}
   * disables loop detection for that element type.
   *
   * <p><b>Multi-instance note:</b> for a multi-instance task the override is keyed by the inner
   * element type (e.g. {@code SERVICE_TASK}) and applies to each child activation, including the
   * parallel child-spawn batch bound; setting the inner type to {@code 0} disables both for those
   * children. {@code MULTI_INSTANCE_BODY} acts as an on/off gate — setting it to {@code 0} disables
   * detection for all multi-instance children regardless of their inner type.
   *
   * <p><b>Nesting note:</b> counters are keyed by process instance and element id, so elements
   * nested inside an embedded subprocess used as a multi-instance inner activity accumulate on one
   * shared counter across all of its children. Size per-type overrides for such nested elements
   * above the largest expected collection to avoid raising incidents on legitimately large
   * collections.
   */
  private Map<BpmnElementType, Integer> maxElementActivationCountByType = new HashMap<>();

  public int getMaxElementActivationCount() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".max-element-activation-count",
        maxElementActivationCount,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MAX_ELEMENT_ACTIVATION_COUNT_PROPERTIES);
  }

  public void setMaxElementActivationCount(final int maxElementActivationCount) {
    this.maxElementActivationCount = maxElementActivationCount;
  }

  public int getElementActivationRetryCooldown() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".element-activation-retry-cooldown",
        elementActivationRetryCooldown,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ELEMENT_ACTIVATION_RETRY_COOLDOWN_PROPERTIES);
  }

  public void setElementActivationRetryCooldown(final int elementActivationRetryCooldown) {
    this.elementActivationRetryCooldown = elementActivationRetryCooldown;
  }

  public Map<BpmnElementType, Integer> getMaxElementActivationCountByType() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".max-element-activation-count-by-type",
        maxElementActivationCountByType,
        ResolvableType.forClassWithGenerics(Map.class, BpmnElementType.class, Integer.class),
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MAX_ELEMENT_ACTIVATION_COUNT_BY_TYPE_PROPERTIES);
  }

  public void setMaxElementActivationCountByType(
      final Map<BpmnElementType, Integer> maxElementActivationCountByType) {
    this.maxElementActivationCountByType =
        maxElementActivationCountByType == null ? new HashMap<>() : maxElementActivationCountByType;
  }
}
