/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clustervariable;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ClusterVariableState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class ClusterVariableProcessors {
  public static void addClusterVariableProcessors(
      final KeyGenerator keyGenerator,
      final TypedRecordProcessors typedRecordProcessors,
      final ClusterVariableState clusterVariableState,
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    typedRecordProcessors.onCommand(
        ValueType.CLUSTER_VARIABLE,
        ClusterVariableIntent.CREATE,
        new ClusterVariableCreateProcessor(
            keyGenerator, writers, authCheckBehavior, distributionBehavior, clusterVariableState));
    typedRecordProcessors.onCommand(
        ValueType.CLUSTER_VARIABLE,
        ClusterVariableIntent.UPDATE,
        new ClusterVariableUpdateProcessor(
            keyGenerator, writers, authCheckBehavior, distributionBehavior, clusterVariableState));
    typedRecordProcessors.onCommand(
        ValueType.CLUSTER_VARIABLE,
        ClusterVariableIntent.DELETE,
        new ClusterVariableDeleteProcessor(
            keyGenerator, writers, authCheckBehavior, distributionBehavior, clusterVariableState));
  }
}
