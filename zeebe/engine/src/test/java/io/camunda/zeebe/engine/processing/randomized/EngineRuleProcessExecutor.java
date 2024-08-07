/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.randomized;

import io.camunda.process.generator.api.ProcessExecutor;
import io.camunda.process.generator.execution.BroadcastSignalStep;
import io.camunda.process.generator.execution.CompleteJobStep;
import io.camunda.process.generator.execution.CompleteUserTaskStep;
import io.camunda.process.generator.execution.CreateProcessInstanceStep;
import io.camunda.process.generator.execution.PublishMessageStep;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;

public class EngineRuleProcessExecutor implements ProcessExecutor {

  private final EngineRule engineRule;

  public EngineRuleProcessExecutor(final EngineRule engineRule) {
    this.engineRule = engineRule;
  }

  @Override
  public void execute(final CreateProcessInstanceStep step) {
    engineRule.processInstance().ofBpmnProcessId(step.processId()).create();
  }

  @Override
  public void execute(final BroadcastSignalStep step) {
    engineRule.signal().withSignalName(step.elementId()).broadcast();
  }

  @Override
  public void execute(final CompleteJobStep step) {
    RecordingExporter.jobRecords(JobIntent.CREATED).withElementId(step.elementId()).await();
    final var jobKey =
        engineRule
            .jobs()
            .withType(step.jobType())
            .withMaxJobsToActivate(1)
            .activate()
            .getValue()
            .getJobKeys()
            .getFirst();
    engineRule.job().withKey(jobKey).complete();
  }

  @Override
  public void execute(final CompleteUserTaskStep step) {
    final var userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withElementId(step.elementId())
            .getFirst()
            .getKey();
    engineRule.userTask().withKey(userTaskKey).complete();
  }

  @Override
  public void execute(final PublishMessageStep step) {
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withMessageName(step.messageName())
        .await();
    engineRule
        .message()
        .withName(step.messageName())
        .withCorrelationKey(step.correlationKey())
        .publish();
  }
}
