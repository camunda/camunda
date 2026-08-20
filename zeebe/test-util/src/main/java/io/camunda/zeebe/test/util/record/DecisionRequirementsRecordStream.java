/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import java.util.stream.Stream;

public class DecisionRequirementsRecordStream
    extends ExporterRecordStream<
        DecisionRequirementsRecordValue, DecisionRequirementsRecordStream> {

  public DecisionRequirementsRecordStream(
      final Stream<Record<DecisionRequirementsRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected DecisionRequirementsRecordStream supply(
      final Stream<Record<DecisionRequirementsRecordValue>> wrappedStream) {
    return new DecisionRequirementsRecordStream(wrappedStream);
  }

  public DecisionRequirementsRecordStream withDecisionRequirementsKey(
      final long decisionRequirementsKey) {
    return valueFilter(v -> v.getDecisionRequirementsKey() == decisionRequirementsKey);
  }

  public DecisionRequirementsRecordStream withDecisionRequirementsId(
      final String decisionRequirementsId) {
    return valueFilter(v -> v.getDecisionRequirementsId().equals(decisionRequirementsId));
  }

  public DecisionRequirementsRecordStream withDecisionRequirementsName(
      final String decisionRequirementsName) {
    return valueFilter(v -> v.getDecisionRequirementsName().equals(decisionRequirementsName));
  }

  public DecisionRequirementsRecordStream withResourceName(final String resourceName) {
    return valueFilter(v -> v.getResourceName().equals(resourceName));
  }

  public DecisionRequirementsRecordStream withDeploymentKey(final long deploymentKey) {
    return valueFilter(v -> v.getDeploymentKey() == deploymentKey);
  }
}
