/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.bpmn.ProcessInstanceStateTransitionGuard;
import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.common.ElementActivationBehavior;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.job.behaviour.JobUpdateBehaviour;
import io.camunda.zeebe.engine.processing.storageordinals.StorageOrdinalProvider;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;

public interface BpmnBehaviors {

  ExpressionProcessor expressionProcessor();

  /**
   * Evaluates an input outside of any process instance, as an input mapping would: secret
   * references become their placeholders and cluster variables resolve, but no process variable is
   * visible. Used for the input of a probe.
   */
  ExpressionProcessor clusterInputExpressionProcessor();

  BpmnDecisionBehavior bpmnDecisionBehavior();

  BpmnVariableMappingBehavior variableMappingBehavior();

  BpmnEventPublicationBehavior eventPublicationBehavior();

  BpmnEventSubscriptionBehavior eventSubscriptionBehavior();

  BpmnIncidentBehavior incidentBehavior();

  BpmnStateBehavior stateBehavior();

  ProcessInstanceStateTransitionGuard stateTransitionGuard();

  BpmnProcessResultSenderBehavior processResultSenderBehavior();

  BpmnBufferedMessageStartEventBehavior bufferedMessageStartEventBehavior();

  BpmnJobBehavior jobBehavior();

  BpmnSignalBehavior signalBehavior();

  MultiInstanceInputCollectionBehavior inputCollectionBehavior();

  MultiInstanceOutputCollectionBehavior outputCollectionBehavior();

  CatchEventBehavior catchEventBehavior();

  EventTriggerBehavior eventTriggerBehavior();

  VariableBehavior variableBehavior();

  ElementActivationBehavior elementActivationBehavior();

  BpmnJobActivationBehavior jobActivationBehavior();

  BpmnUserTaskBehavior userTaskBehavior();

  BpmnCompensationSubscriptionBehaviour compensationSubscriptionBehaviour();

  JobUpdateBehaviour jobUpdateBehaviour();

  BpmnAdHocSubProcessBehavior adHocSubProcessBehavior();

  AgentInstanceBehavior agentInstanceBehavior();

  AgentDefinitionBehavior agentDefinitionBehavior();

  BpmnProcessDeletionBehavior processDeletionBehavior();

  StorageOrdinalProvider storageOrdinalProvider();
}
