/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.test.util.bpmn.random.steps.AbstractExecutionStep;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepActivateAndCompleteJob;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepActivateAndFailJob;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepActivateAndTimeoutJob;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepActivateJobAndThrowError;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepCompleteUserTask;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepPublishMessage;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepPublishStartMessage;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepRaiseIncidentThenResolveAndPickConditionCase;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepStartProcessInstance;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepThrowError;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepTriggerTimerBoundaryEvent;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepTriggerTimerStartEvent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import org.awaitility.Awaitility;

/** This class executes individual {@link AbstractExecutionStep} for a given process */
public class ProcessExecutor {

  private final EngineRule engineRule;

  public ProcessExecutor(final EngineRule engineRule) {
    this.engineRule = engineRule;
  }

  public void applyStep(final AbstractExecutionStep step) {

    if (step.isAutomatic()) {
      // Nothing to do here, as the step execution is controlled by the engine
    } else if (step instanceof StepStartProcessInstance) {
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
    } else if (step instanceof StepTriggerTimerBoundaryEvent) {
      final StepTriggerTimerBoundaryEvent timeoutElement = (StepTriggerTimerBoundaryEvent) step;
      triggerTimerBoundaryEvent(timeoutElement);
    } else if (step instanceof StepActivateJobAndThrowError) {
      final StepActivateJobAndThrowError activateJobAndThrowError =
          (StepActivateJobAndThrowError) step;
      activateJobAndThrowError(activateJobAndThrowError);
    } else if (step instanceof StepRaiseIncidentThenResolveAndPickConditionCase) {
      final var expressionIncident = (StepRaiseIncidentThenResolveAndPickConditionCase) step;
      resolveExpressionIncident(expressionIncident);
    } else if (step instanceof StepTriggerTimerStartEvent) {
      final StepTriggerTimerStartEvent timerStep = (StepTriggerTimerStartEvent) step;
      triggerTimerStartEvent(timerStep);
    } else if (step instanceof StepCompleteUserTask) {
      final StepCompleteUserTask stepCompleteUserTask = (StepCompleteUserTask) step;
      completeUserTask(stepCompleteUserTask);
    } else if (step instanceof StepThrowError) {
      final StepThrowError stepThrowError = (StepThrowError) step;
      throwError(stepThrowError);
    } else {
      throw new IllegalStateException("Not yet implemented: " + step);
    }
  }

  private void triggerTimerBoundaryEvent(final StepTriggerTimerBoundaryEvent timeoutElement) {
    final var timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withHandlerNodeId(timeoutElement.getBoundaryTimerEventId())
            .getFirst();

    waitUntilRecordIsProcessed("await the timer to be processed", timerCreated);

    engineRule.getClock().addTime(timeoutElement.getDeltaTime());

    RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
        .withHandlerNodeId(timeoutElement.getBoundaryTimerEventId())
        .await();
  }

  private void activateAndCompleteJob(final StepActivateAndCompleteJob activateAndCompleteJob) {
    waitForJobToBeCreated(activateAndCompleteJob.getElementId());

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
    waitForJobToBeCreated(activateAndFailJob.getElementId());

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
    waitForJobToBeCreated(activateAndTimeoutJob.getElementId());

    engineRule.jobs().withType(activateAndTimeoutJob.getJobType()).withTimeout(100).activate();

    final var activatedJobBatch =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED)
            .withType(activateAndTimeoutJob.getJobType())
            .getFirst();

    waitUntilRecordIsProcessed("await job batch to be processed", activatedJobBatch);

    engineRule.getClock().addTime(activateAndTimeoutJob.getDeltaTime());

    RecordingExporter.jobRecords(JobIntent.TIME_OUT)
        .withType(activateAndTimeoutJob.getJobType())
        .await();
  }

  private void activateJobAndThrowError(
      final StepActivateJobAndThrowError stepActivateJobAndThrowError) {
    waitForJobToBeCreated(stepActivateJobAndThrowError.getElementId());

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

  private Record<JobRecordValue> waitForJobToBeCreated(final String elementId) {
    return RecordingExporter.jobRecords(JobIntent.CREATED).withElementId(elementId).getFirst();
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
        .withVariables(publishMessage.getProcessVariables())
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
        .withVariables(startProcess.getProcessVariables())
        .create();
  }

  private void triggerTimerStartEvent(final StepTriggerTimerStartEvent timerStep) {
    final Record<TimerRecordValue> timerSchedulingRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED).getFirst();
    waitUntilRecordIsProcessed("until start timer is scheduled", timerSchedulingRecord);

    engineRule.increaseTime(timerStep.getDeltaTime());

    // await that the timer is triggered or otherwise there may be a race condition where a test may
    // think we've already reached a wait state, when in truth the timer trigger hasn't even been
    // processed and so we haven't actually moved on from the previous wait state
    RecordingExporter.timerRecords(TimerIntent.TRIGGERED).await();
  }

  private void completeUserTask(final StepCompleteUserTask completeUserTask) {
    final Record<JobRecordValue> jobRecord = waitForJobToBeCreated(completeUserTask.getElementId());

    engineRule.job().withKey(jobRecord.getKey()).complete();
  }

  private void throwError(final StepThrowError stepThrowError) {
    final Record<JobRecordValue> jobRecord = waitForJobToBeCreated(stepThrowError.getElementId());

    engineRule
        .job()
        .withKey(jobRecord.getKey())
        .withErrorCode(stepThrowError.getErrorCode())
        .throwError();
  }

  private void resolveExpressionIncident(
      final StepRaiseIncidentThenResolveAndPickConditionCase expressionIncident) {
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

  private void waitUntilRecordIsProcessed(final String condition, final Record<?> record) {
    Awaitility.await(condition)
        .until(() -> engineRule.getLastProcessedPosition() >= record.getPosition());
  }
}
