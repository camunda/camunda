/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.kafka.config;

import io.camunda.zeebe.protocol.record.ValueType;
import java.util.EnumMap;
import java.util.Map;

@SuppressWarnings("squid:ClassVariableVisibilityCheck")
public final class RawRecordsConfig {
  public RawRecordConfig defaults;
  // Definition records — exported only from partition 1 (see
  // RecordHandler.PARTITION_ONE_VALUE_TYPES)
  public RawRecordConfig authorization;
  public RawRecordConfig decision;
  public RawRecordConfig decisionRequirements;
  public RawRecordConfig form;
  public RawRecordConfig group;
  public RawRecordConfig mappingRule;
  public RawRecordConfig process;
  public RawRecordConfig role;
  public RawRecordConfig tenant;
  public RawRecordConfig user;
  // Runtime records
  public RawRecordConfig commandDistribution;
  public RawRecordConfig compensation;
  public RawRecordConfig compensationSubscription;
  public RawRecordConfig decisionEvaluation;
  public RawRecordConfig deployment;
  public RawRecordConfig deploymentDistribution;
  public RawRecordConfig error;
  public RawRecordConfig escalationSubscription;
  public RawRecordConfig incident;
  public RawRecordConfig job;
  public RawRecordConfig jobBatch;
  public RawRecordConfig message;
  public RawRecordConfig messageSubscription;
  public RawRecordConfig messageStartEventSubscription;
  public RawRecordConfig processEvent;
  public RawRecordConfig processInstance;
  public RawRecordConfig processInstanceBatch;
  public RawRecordConfig processInstanceCreation;
  public RawRecordConfig processInstanceMigration;
  public RawRecordConfig processInstanceModification;
  public RawRecordConfig processInstanceResult;
  public RawRecordConfig processMessageSubscription;
  public RawRecordConfig resourceDeletion;
  public RawRecordConfig signal;
  public RawRecordConfig signalSubscription;
  public RawRecordConfig timer;
  public RawRecordConfig userTask;
  public RawRecordConfig variable;
  public RawRecordConfig variableDocument;

  /**
   * Returns a map of all non-null per-value-type overrides, keyed by {@link ValueType}.
   *
   * <p>This method is explicit — no reflection, no camelCase→UPPER_SNAKE_CASE conversion — so it is
   * safe under GraalVM native image and gives compile-time visibility of the field-to-type mapping.
   */
  public Map<ValueType, RawRecordConfig> byValueType() {
    final Map<ValueType, RawRecordConfig> result = new EnumMap<>(ValueType.class);
    // Definition records
    put(result, ValueType.AUTHORIZATION, authorization);
    put(result, ValueType.DECISION, decision);
    put(result, ValueType.DECISION_REQUIREMENTS, decisionRequirements);
    put(result, ValueType.FORM, form);
    put(result, ValueType.GROUP, group);
    put(result, ValueType.MAPPING_RULE, mappingRule);
    put(result, ValueType.PROCESS, process);
    put(result, ValueType.ROLE, role);
    put(result, ValueType.TENANT, tenant);
    put(result, ValueType.USER, user);
    // Runtime records
    put(result, ValueType.COMMAND_DISTRIBUTION, commandDistribution);
    // Note: no ValueType.COMPENSATION exists in the protocol; 'compensation' field is unused.
    put(result, ValueType.COMPENSATION_SUBSCRIPTION, compensationSubscription);
    put(result, ValueType.DECISION_EVALUATION, decisionEvaluation);
    put(result, ValueType.DEPLOYMENT, deployment);
    put(result, ValueType.DEPLOYMENT_DISTRIBUTION, deploymentDistribution);
    put(result, ValueType.ERROR, error);
    // The protocol uses ESCALATION (not ESCALATION_SUBSCRIPTION) for escalation records.
    put(result, ValueType.ESCALATION, escalationSubscription);
    put(result, ValueType.INCIDENT, incident);
    put(result, ValueType.JOB, job);
    put(result, ValueType.JOB_BATCH, jobBatch);
    put(result, ValueType.MESSAGE, message);
    put(result, ValueType.MESSAGE_SUBSCRIPTION, messageSubscription);
    put(result, ValueType.MESSAGE_START_EVENT_SUBSCRIPTION, messageStartEventSubscription);
    put(result, ValueType.PROCESS_EVENT, processEvent);
    put(result, ValueType.PROCESS_INSTANCE, processInstance);
    put(result, ValueType.PROCESS_INSTANCE_BATCH, processInstanceBatch);
    put(result, ValueType.PROCESS_INSTANCE_CREATION, processInstanceCreation);
    put(result, ValueType.PROCESS_INSTANCE_MIGRATION, processInstanceMigration);
    put(result, ValueType.PROCESS_INSTANCE_MODIFICATION, processInstanceModification);
    put(result, ValueType.PROCESS_INSTANCE_RESULT, processInstanceResult);
    put(result, ValueType.PROCESS_MESSAGE_SUBSCRIPTION, processMessageSubscription);
    put(result, ValueType.RESOURCE_DELETION, resourceDeletion);
    put(result, ValueType.SIGNAL, signal);
    put(result, ValueType.SIGNAL_SUBSCRIPTION, signalSubscription);
    put(result, ValueType.TIMER, timer);
    put(result, ValueType.USER_TASK, userTask);
    put(result, ValueType.VARIABLE, variable);
    put(result, ValueType.VARIABLE_DOCUMENT, variableDocument);
    return result;
  }

  private static void put(
      final Map<ValueType, RawRecordConfig> map,
      final ValueType valueType,
      final RawRecordConfig config) {
    if (config != null) {
      map.put(valueType, config);
    }
  }
}
