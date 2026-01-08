/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors;

/**
 * Marker interface for descriptors that are dependant on decision instances. Used to identify
 * related documents that need to be archived together with decision instances.
 *
 * <p>When implementing this inteface, the {@code getDecisionDependantField} method can be
 * overridden to specify a different field name if the default {@code DECISION_INSTANCE_KEY} does
 * not apply.
 */
public interface DecisionInstanceDependant {

  String DECISION_INSTANCE_KEY = "decisionDefinitionKey";

  String getFullQualifiedName();

  default String getDecisionDependantField() {
    return DECISION_INSTANCE_KEY;
  }
}
