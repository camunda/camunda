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
