/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.coverage.data;

import io.camunda.client.api.search.response.ProcessDefinition;
import org.immutables.value.Value;

/**
 * Coverage input data for a deployed process definition.
 *
 * <p>Contains the process definition metadata and BPMN XML required to calculate and render
 * definition-level coverage.
 */
@Value.Immutable
public interface CoverageProcessDefinitionData {

  /** Returns the deployed process definition metadata. */
  ProcessDefinition getProcessDefinition();

  /** Returns the BPMN XML of the process definition. */
  String getXml();
}
