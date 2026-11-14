/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

/**
 * Strategy that produces the BPMN 2.0 XML resource for a process definition.
 *
 * <p>Separating the provider as a functional interface decouples {@link ZeebeRecordFactory} from
 * any concrete XML-generation class, satisfying the Dependency Inversion Principle.
 *
 * <p>The default implementation is {@code ProcessBpmnBuilder::bpmn}, supplied at wiring time in
 * {@link ZeebeProcessDataGenerator}.
 */
@FunctionalInterface
interface BpmnProvider {

  /** Returns the BPMN 2.0 XML bytes for the given process definition ID. */
  byte[] bpmnFor(String processId);
}
