/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.fact;

/**
 * The kind of entity a {@link Fact} describes. This replaces routing by concrete fact class ({@code
 * TypeRoutingAggregation} keyed on {@code Class}) with a stable tag a dataset declares its source
 * as — so one generic fact type serves every metric.
 */
public enum FactType {
  PROCESS_INSTANCE,
  ELEMENT,
  INCIDENT,
  PROCESS_DEFINITION
}
