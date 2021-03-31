/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.util;

import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.bpmn.random.steps.AbstractExecutionStep;
import io.zeebe.test.util.bpmn.random.steps.StepActivateAndCompleteJob;
import io.zeebe.test.util.bpmn.random.steps.StepActivateAndFailJob;
import io.zeebe.test.util.bpmn.random.steps.StepActivateAndTimeoutJob;
import io.zeebe.test.util.bpmn.random.steps.StepActivateJobAndThrowError;
import io.zeebe.test.util.bpmn.random.steps.StepExpressionIncidentCase;
import io.zeebe.test.util.bpmn.random.steps.StepPickConditionCase;
import io.zeebe.test.util.bpmn.random.steps.StepPickDefaultCase;
import io.zeebe.test.util.bpmn.random.steps.StepPublishMessage;
import io.zeebe.test.util.bpmn.random.steps.StepPublishStartMessage;
import io.zeebe.test.util.bpmn.random.steps.StepStartProcessInstance;
import io.zeebe.test.util.bpmn.random.steps.StepTriggerTimer;
import io.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;

/** This class executes individual {@link AbstractExecutionStep} for a given process */
public class ProcessExecutor {

  private final EngineRule engineRule;

  public ProcessExecutor(final EngineRule engineRule) {
    this.engineRule = engineRule;
  }

  public void applyStep(final AbstractExecutionStep step) {

    if (step instanceof StepStartProcessInstance) {
      final StepStartProcessInstance startProcess = (StepStartProcessInstance) step;
      createProcessInstance(startProcess);
    } else if (step instanceof StepPublishStartMessage) {
      final StepPublishStartMessage publishMessage = (StepPublishStartMessage) step;
      publishStartMessage(publishMessage);
    } else if (step instanceof StepPublishMessage) {
      final StepPublishMessage publishMessage = (StepPublishMessage) step;
      publishMessage(publishMessage);
    } else if (step instanceof StepActivateAndCompleteJob) {
      final StepActivateAndCompleteJob activateAndCompleteJob = (StepActivateAndCompleteJob) step;
      activateAndCompleteJob(activateAndCompleteJob);
    } else if (step instanceof StepActivateAndFailJob) {
      final StepActivateAndFailJob activateAndFailJob = (StepActivateAndFailJob) step;
      activateAndFailJob(activateAndFailJob);
    } else if (step instanceof StepActivateAndTimeoutJob) {
      final StepActivateAndTimeoutJob activateAndTimeoutJob = (StepActivateAndTimeoutJob) step;
      activateAndTimeoutJob(activateAndTimeoutJob);
    } else if (step instanceof StepActivateJobAndThrowError) {
      final StepActivateJobAndThrowError activateJobAndThrowError =
          (StepActivateJobAndThrowError) step;
      activateJobAndThrowError(activateJobAndThrowError);
    } else if ((step instanceof StepPickDefaultCase) || (step instanceof StepPickConditionCase)) {
      /*
       * Nothing to do here, as the choice is made by the engine. The default case is for debugging
       * purposes only The condition case is implemented by starting the process with the right
       * variables;
       *
       * One thing that might be a useful addition here is to wait until a certain path was taken to improve debugging
       */
    } else if (step instanceof StepExpressionIncidentCase) {
      final var expressionIncident = (StepExpressionIncidentCase) step;
      resolveExpressionIncident(expressionIncident);
    } else if (step instanceof StepTriggerTimer) {
      final StepTriggerTimer timerStep = (StepTriggerTimer) step;
      triggerTimer(timerStep);
    } else {
      throw new IllegalStateException("Not yet implemented: " + step);
    }
  }

  private void activateAndCompleteJob(final StepActivateAndCompleteJob activateAndCompleteJob) {
    waitForJobToBeCreated(activateAndCompleteJob.getJobType());

    final Map<String, Object> variables = activateAndCompleteJob.getVariables();
    engineRule
        .jobs()
        .withType(activateAndCompleteJob.getJobType())
        .activate()
        .getValue()
        .getJobKeys()
        .forEach(jobKey -> engineRule.job().withKey(jobKey).withVariables(variables).complete());
  }

  private void activateAndFailJob(final StepActivateAndFailJob activateAndFailJob) {
    waitForJobToBeCreated(activateAndFailJob.getJobType());

    if (activateAndFailJob.isUpdateRetries()) {
      engineRule
          .jobs()
          .withType(activateAndFailJob.getJobType())
          .activate()
          .getValue()
          .getJobKeys()
          .forEach(
              jobKey -> {
                engineRule.job().withKey(jobKey).withRetries(0).fail();

                final var incidentRecord =
                    RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                        .withJobKey(jobKey)
                        .findFirst()
                        .get();

                engineRule.job().withKey(jobKey).withRetries(3).updateRetries();

                engineRule
                    .incident()
                    .ofInstance(incidentRecord.getValue().getProcessInstanceKey())
                    .withKey(incidentRecord.getKey())
                    .resolve();
                RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
                    .withJobKey(jobKey)
                    .await();
              });

    } else {
      engineRule
          .jobs()
          .withType(activateAndFailJob.getJobType())
          .activate()
          .getValue()
          .getJobKeys()
          .forEach(jobKey -> engineRule.job().withKey(jobKey).withRetries(3).fail());
    }
  }

  private void activateAndTimeoutJob(final StepActivateAndTimeoutJob activateAndTimeoutJob) {
    waitForJobToBeCreated(activateAndTimeoutJob.getJobType());

    engineRule.jobs().withType(activateAndTimeoutJob.getJobType()).withTimeout(100).activate();

    engineRule.getClock().addTime(Duration.ofSeconds(150));

    RecordingExporter.jobRecords(JobIntent.TIME_OUT)
        .withType(activateAndTimeoutJob.getJobType())
        .await();
  }

  private void activateJobAndThrowError(
      final StepActivateJobAndThrowError stepActivateJobAndThrowError) {
    waitForJobToBeCreated(stepActivateJobAndThrowError.getJobType());

    engineRule
        .jobs()
        .withType(stepActivateJobAndThrowError.getJobType())
        .withTimeout(100)
        .activate()
        .getValue()
        .getJobKeys()
        .forEach(
            jobKey -> {
              engineRule
                  .job()
                  .withKey(jobKey)
                  .withErrorCode(stepActivateJobAndThrowError.getErrorCode())
                  .throwError();
            });
  }

  private void waitForJobToBeCreated(final String jobType) {
    RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).await();
  }

  private void publishMessage(final StepPublishMessage publishMessage) {
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withMessageName(publishMessage.getMessageName())
        .withCorrelationKey(publishMessage.getCorrelationKeyValue())
        .await();

    engineRule
        .message()
        .withName(publishMessage.getMessageName())
        .withCorrelationKey(publishMessage.getCorrelationKeyValue())
        .withVariables(publishMessage.getVariables())
        .publish();

    /*
     * If we don't wait for the message to be correlated, then this will happen asynchronously.
     * Especially in ReplayStatePropertyTest this prevents us from capturing database
     * state at precise points where we know that the system is idle.
     */
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
        .withMessageName(publishMessage.getMessageName())
        .await();
  }

  private void publishStartMessage(final StepPublishStartMessage publishMessage) {
    RecordingExporter.messageStartEventSubscriptionRecords(
            MessageStartEventSubscriptionIntent.CREATED)
        .withMessageName(publishMessage.getMessageName())
        .await();

    engineRule
        .message()
        .withName(publishMessage.getMessageName())
        .withCorrelationKey("")
        .withVariables(publishMessage.getVariables())
        .publish();

    RecordingExporter.messageStartEventSubscriptionRecords(
            MessageStartEventSubscriptionIntent.CORRELATED)
        .withMessageName(publishMessage.getMessageName())
        .await();
  }

  private void createProcessInstance(final StepStartProcessInstance startProcess) {
    engineRule
        .processInstance()
        .ofBpmnProcessId(startProcess.getProcessId())
        .withVariables(startProcess.getVariables())
        .create();
  }

  private void triggerTimer(final StepTriggerTimer timerStep) {
    engineRule.increaseTime(timerStep.getTimeToAdd());
  }

  private void resolveExpressionIncident(final StepExpressionIncidentCase expressionIncident) {
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withElementId(expressionIncident.getGatewayElementId())
            .findFirst()
            .get();

    engineRule
        .variables()
        .ofScope(incident.getValue().getProcessInstanceKey())
        .withDocument(
            MsgPackUtil.asMsgPack(
                Map.of(
                    expressionIncident.getGatewayConditionVariable(),
                    expressionIncident.getEdgeId())))
        .withUpdateSemantic(VariableDocumentUpdateSemantic.LOCAL)
        .update();

    engineRule
        .incident()
        .ofInstance(incident.getValue().getProcessInstanceKey())
        .withKey(incident.getKey())
        .resolve();
    RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
        .withElementId(expressionIncident.getGatewayElementId())
        .await();
  }
}
