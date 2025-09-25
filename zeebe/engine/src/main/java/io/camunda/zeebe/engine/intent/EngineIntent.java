/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

import io.camunda.zeebe.engine.intent.management.CheckpointEngineIntent;
import io.camunda.zeebe.engine.intent.scaling.ScaleEngineIntent;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Arrays;
import java.util.Collection;

public interface EngineIntent {
  Collection<Class<? extends EngineIntent>> INTENT_CLASSES =
      Arrays.asList(
          DeploymentEngineIntent.class,
          EscalationEngineIntent.class,
          IncidentEngineIntent.class,
          JobEngineIntent.class,
          ProcessInstanceEngineIntent.class,
          MessageEngineIntent.class,
          MessageBatchEngineIntent.class,
          MessageSubscriptionEngineIntent.class,
          ProcessMessageSubscriptionEngineIntent.class,
          JobBatchEngineIntent.class,
          TimerEngineIntent.class,
          VariableEngineIntent.class,
          VariableDocumentEngineIntent.class,
          ProcessInstanceCreationEngineIntent.class,
          ErrorEngineIntent.class,
          ProcessEngineIntent.class,
          DeploymentDistributionEngineIntent.class,
          ProcessEventEngineIntent.class,
          DecisionEngineIntent.class,
          DecisionRequirementsEngineIntent.class,
          DecisionEvaluationEngineIntent.class,
          MessageStartEventSubscriptionEngineIntent.class,
          ProcessInstanceResultEngineIntent.class,
          CheckpointEngineIntent.class,
          ProcessInstanceModificationEngineIntent.class,
          SignalEngineIntent.class,
          SignalSubscriptionEngineIntent.class,
          ResourceDeletionEngineIntent.class,
          CommandDistributionEngineIntent.class,
          ProcessInstanceBatchEngineIntent.class,
          FormEngineIntent.class,
          ResourceEngineIntent.class,
          UserTaskEngineIntent.class,
          ProcessInstanceMigrationEngineIntent.class,
          CompensationSubscriptionEngineIntent.class,
          MessageCorrelationEngineIntent.class,
          UserEngineIntent.class,
          ClockEngineIntent.class,
          AuthorizationEngineIntent.class,
          RoleEngineIntent.class,
          TenantEngineIntent.class,
          ScaleEngineIntent.class,
          GroupEngineIntent.class,
          MappingRuleEngineIntent.class,
          IdentitySetupEngineIntent.class,
          BatchOperationEngineIntent.class,
          BatchOperationChunkEngineIntent.class,
          BatchOperationExecutionEngineIntent.class,
          AdHocSubProcessInstructionEngineIntent.class,
          AsyncRequestEngineIntent.class,
          UsageMetricEngineIntent.class,
          MultiInstanceEngineIntent.class,
          RuntimeInstructionEngineIntent.class);
  short NULL_VAL = 255;
  EngineIntent UNKNOWN = UnknownEngineIntent.UNKNOWN;

  String name();

  io.camunda.zeebe.protocol.record.intent.Intent protocolIntent();

  void registerEventAppliers(EventAppliers eventAppliers, MutableProcessingState state);

  /**
   * @return true if this intent is used as an event, i.e. it's not a command or command rejection.
   */
  boolean isEvent();

  @SuppressWarnings("checkstyle:MissingSwitchDefault")
  enum UnknownEngineIntent implements EngineIntent {
    UNKNOWN;

    @Override
    public Intent protocolIntent() {
      return Intent.UNKNOWN;
    }

    @Override
    public boolean isEvent() {
      return false;
    }
  }
}
