/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration.IndexConfiguration;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.EnumSet;
import java.util.stream.Stream;

/** Collection of utilities for unit and integration tests. */
public final class TestSupport {

  private TestSupport() {}

  /**
   * Sets the correct indexing configuration field for the given value type. This is particularly
   * helpful for parameterized tests.
   *
   * <p>TODO: this is terrible, but the configuration is also terrible to use programmatically
   */
  @SuppressWarnings("checkstyle:innerassignment")
  public static void setIndexingForValueType(
      final IndexConfiguration config, final ValueType valueType, final boolean value) {
    switch (valueType) {
      case JOB -> config.job = value;
      case DEPLOYMENT -> config.deployment = value;
      case PROCESS_INSTANCE -> config.processInstance = value;
      case PROCESS_INSTANCE_BATCH -> config.processInstanceBatch = value;
      case INCIDENT -> config.incident = value;
      case MESSAGE -> config.message = value;
      case MESSAGE_BATCH -> config.messageBatch = value;
      case MESSAGE_SUBSCRIPTION -> config.messageSubscription = value;
      case PROCESS_MESSAGE_SUBSCRIPTION -> config.processMessageSubscription = value;
      case JOB_BATCH -> config.jobBatch = value;
      case VARIABLE -> config.variable = value;
      case VARIABLE_DOCUMENT -> config.variableDocument = value;
      case PROCESS_INSTANCE_CREATION -> config.processInstanceCreation = value;
      case PROCESS_INSTANCE_MIGRATION -> config.processInstanceMigration = value;
      case PROCESS_INSTANCE_MODIFICATION -> config.processInstanceModification = value;
      case ERROR -> config.error = value;
      case PROCESS -> config.process = value;
      case DECISION -> config.decision = value;
      case DECISION_REQUIREMENTS -> config.decisionRequirements = value;
      case DECISION_EVALUATION -> config.decisionEvaluation = value;
      case CHECKPOINT -> config.checkpoint = value;
      case TIMER -> config.timer = value;
      case MESSAGE_START_EVENT_SUBSCRIPTION -> config.messageStartEventSubscription = value;
      case PROCESS_EVENT -> config.processEvent = value;
      case DEPLOYMENT_DISTRIBUTION -> config.deploymentDistribution = value;
      case ESCALATION -> config.escalation = value;
      case SIGNAL -> config.signal = value;
      case SIGNAL_SUBSCRIPTION -> config.signalSubscription = value;
      case RESOURCE_DELETION -> config.resourceDeletion = value;
      case COMMAND_DISTRIBUTION -> config.commandDistribution = value;
      case FORM -> config.form = value;
      case USER_TASK -> config.userTask = value;
      case COMPENSATION_SUBSCRIPTION -> config.compensationSubscription = value;
      case MESSAGE_CORRELATION -> config.messageCorrelation = value;
      case USER -> config.user = value;
      case AUTHORIZATION -> config.authorization = value;
      case BATCH_OPERATION_CREATION -> config.batchOperationCreation = value;
      case BATCH_OPERATION_CHUNK -> config.batchOperationChunk = value;
      case BATCH_OPERATION_EXECUTION -> config.batchOperationExecution = value;
      case BATCH_OPERATION_LIFECYCLE_MANAGEMENT -> config.batchOperationLifecycleManagement = value;
      case BATCH_OPERATION_PARTITION_LIFECYCLE -> config.batchOperationPartitionLifecycle = value;
      case BATCH_OPERATION_INITIALIZATION -> config.batchOperationInitialization = value;
      case AD_HOC_SUB_PROCESS_INSTRUCTION -> config.adHocSubProcessInstruction = value;
      case ASYNC_REQUEST -> config.asyncRequest = value;
      case USAGE_METRIC -> config.usageMetrics = value;
      case RUNTIME_INSTRUCTION -> config.runtimeInstruction = value;
      default ->
          throw new IllegalArgumentException(
              "No known indexing configuration option for value type " + valueType);
    }
  }

  @SuppressWarnings("checkstyle:innerassignment")
  public static void setIndexingForValueType(
      final OpensearchExporterConfiguration.IndexConfiguration config,
      final ValueType valueType,
      final boolean value) {
    switch (valueType) {
      case JOB -> config.job = value;
      case DEPLOYMENT -> config.deployment = value;
      case PROCESS_INSTANCE -> config.processInstance = value;
      case PROCESS_INSTANCE_BATCH -> config.processInstanceBatch = value;
      case INCIDENT -> config.incident = value;
      case MESSAGE -> config.message = value;
      case MESSAGE_BATCH -> config.messageBatch = value;
      case MESSAGE_SUBSCRIPTION -> config.messageSubscription = value;
      case PROCESS_MESSAGE_SUBSCRIPTION -> config.processMessageSubscription = value;
      case JOB_BATCH -> config.jobBatch = value;
      case VARIABLE -> config.variable = value;
      case VARIABLE_DOCUMENT -> config.variableDocument = value;
      case PROCESS_INSTANCE_CREATION -> config.processInstanceCreation = value;
      case PROCESS_INSTANCE_MIGRATION -> config.processInstanceMigration = value;
      case PROCESS_INSTANCE_MODIFICATION -> config.processInstanceModification = value;
      case ERROR -> config.error = value;
      case PROCESS -> config.process = value;
      case DECISION -> config.decision = value;
      case DECISION_REQUIREMENTS -> config.decisionRequirements = value;
      case DECISION_EVALUATION -> config.decisionEvaluation = value;
      case CHECKPOINT -> config.checkpoint = value;
      case TIMER -> config.timer = value;
      case MESSAGE_START_EVENT_SUBSCRIPTION -> config.messageStartEventSubscription = value;
      case PROCESS_EVENT -> config.processEvent = value;
      case DEPLOYMENT_DISTRIBUTION -> config.deploymentDistribution = value;
      case ESCALATION -> config.escalation = value;
      case SIGNAL -> config.signal = value;
      case SIGNAL_SUBSCRIPTION -> config.signalSubscription = value;
      case RESOURCE_DELETION -> config.resourceDeletion = value;
      case COMMAND_DISTRIBUTION -> config.commandDistribution = value;
      case FORM -> config.form = value;
      case USER_TASK -> config.userTask = value;
      case COMPENSATION_SUBSCRIPTION -> config.compensationSubscription = value;
      case MESSAGE_CORRELATION -> config.messageCorrelation = value;
      case USER -> config.user = value;
      case AUTHORIZATION -> config.authorization = value;
      case BATCH_OPERATION_CREATION -> config.batchOperationCreation = value;
      case BATCH_OPERATION_CHUNK -> config.batchOperationChunk = value;
      case BATCH_OPERATION_EXECUTION -> config.batchOperationExecution = value;
      case BATCH_OPERATION_LIFECYCLE_MANAGEMENT -> config.batchOperationLifecycleManagement = value;
      case BATCH_OPERATION_PARTITION_LIFECYCLE -> config.batchOperationPartitionLifecycle = value;
      case BATCH_OPERATION_INITIALIZATION -> config.batchOperationInitialization = value;
      case AD_HOC_SUB_PROCESS_INSTRUCTION -> config.adHocSubProcessInstruction = value;
      case ASYNC_REQUEST -> config.asyncRequest = value;
      case USAGE_METRIC -> config.usageMetrics = value;
      case RUNTIME_INSTRUCTION -> config.runtimeInstruction = value;
      default ->
          throw new IllegalArgumentException(
              "No known indexing configuration option for value type " + valueType);
    }
  }

  /**
   * Returns a stream of value types which are export-able by the exporter, i.e. the ones with an
   * index template.
   *
   * <p>Issue https://github.com/camunda/camunda/issues/8337 should fix this and ensure all types
   * have an index template.
   */
  public static Stream<ValueType> provideValueTypes() {
    final var excludedValueTypes =
        EnumSet.of(
            ValueType.SBE_UNKNOWN,
            ValueType.NULL_VAL,
            ValueType.PROCESS_INSTANCE_RESULT,
            ValueType.CLOCK,
            ValueType.SCALE,
            // these are not yet supported
            ValueType.ROLE,
            ValueType.TENANT,
            ValueType.GROUP,
            ValueType.MAPPING_RULE,
            ValueType.IDENTITY_SETUP,
            ValueType.RESOURCE,
            ValueType.MULTI_INSTANCE);
    return EnumSet.complementOf(excludedValueTypes).stream();
  }
}
