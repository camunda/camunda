/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_ELEMENT_ACTIVATION_RETRY_COOLDOWN;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MAX_ELEMENT_ACTIVATION_COUNT;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.HashMap;
import java.util.Map;

/**
 * Configures loop detection, which raises an incident when a BPMN element is activated more times
 * than allowed within a single process instance.
 *
 * <p>The threshold can be configured per {@link BpmnElementType}: an entry in {@link
 * #maxElementActivationCountByType} overrides {@link #maxElementActivationCount} for that element
 * type, and a per-type value of {@code 0} disables loop detection for that element type.
 *
 * <p><b>Multi-instance note:</b> for a multi-instance task the per-type override is keyed by the
 * inner element type (e.g. {@code SERVICE_TASK}) and applies to each child activation, including
 * the parallel child-spawn batch bound; setting the inner type to {@code 0} disables both for those
 * children. {@code MULTI_INSTANCE_BODY} acts as an on/off gate — setting it to {@code 0} disables
 * detection for all multi-instance children regardless of their inner type.
 *
 * <p><b>Nesting note:</b> counters are keyed by process instance and element id, so elements nested
 * inside an embedded subprocess used as a multi-instance inner activity accumulate on one shared
 * counter across all of its children. Set per-type overrides for such nested elements above the
 * largest expected collection to avoid raising incidents on legitimately large collections.
 */
public class LoopDetectionCfg implements ConfigurationEntry {

  private int maxElementActivationCount = DEFAULT_MAX_ELEMENT_ACTIVATION_COUNT;
  private int elementActivationRetryCooldown = DEFAULT_ELEMENT_ACTIVATION_RETRY_COOLDOWN;
  private Map<BpmnElementType, Integer> maxElementActivationCountByType = new HashMap<>();

  public int getMaxElementActivationCount() {
    return maxElementActivationCount;
  }

  public void setMaxElementActivationCount(final int maxElementActivationCount) {
    this.maxElementActivationCount = maxElementActivationCount;
  }

  public int getElementActivationRetryCooldown() {
    return elementActivationRetryCooldown;
  }

  public void setElementActivationRetryCooldown(final int elementActivationRetryCooldown) {
    this.elementActivationRetryCooldown = elementActivationRetryCooldown;
  }

  public Map<BpmnElementType, Integer> getMaxElementActivationCountByType() {
    return maxElementActivationCountByType;
  }

  public void setMaxElementActivationCountByType(
      final Map<BpmnElementType, Integer> maxElementActivationCountByType) {
    this.maxElementActivationCountByType =
        maxElementActivationCountByType == null ? new HashMap<>() : maxElementActivationCountByType;
  }

  @Override
  public String toString() {
    return "LoopDetectionCfg{"
        + "maxElementActivationCount="
        + maxElementActivationCount
        + ", elementActivationRetryCooldown="
        + elementActivationRetryCooldown
        + ", maxElementActivationCountByType="
        + maxElementActivationCountByType
        + '}';
  }
}
