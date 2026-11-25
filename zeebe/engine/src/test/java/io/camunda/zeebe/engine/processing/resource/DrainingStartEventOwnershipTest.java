/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Verifies that only the latest active version of a process holds its start events, also while a
 * newer version is draining. Runs for every start event type that is bound to a version.
 */
@RunWith(Parameterized.class)
public class DrainingStartEventOwnershipTest {

  private static final String JOB_TYPE = "task";
  private static final String NONE_START_ID = "none-start";
  private static final String TRIGGERED_START_ID = "triggered-start";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Parameter public StartEventType startEventType;

  @Parameters(name = "{0}")
  public static Object[] startEventTypes() {
    return StartEventType.values();
  }

  @Test
  public void shouldHandOverStartEventWhenDeletingVersionThatTookItOver() {
    // given - v3 is draining, so v2 took over the start event
    final var processId = Strings.newRandomValidBpmnId();
    final long v1Key = deploy(processId, "task-v1");
    final long v2Key = deploy(processId, "task-v2");
    final long v3Key = deploy(processId, "task-v3");
    createInstanceAndAwaitJob(processId, 3);
    deleteAndAwait(v3Key, ProcessIntent.DRAINING);

    // when - v2 has no instances, so it is deleted right away
    deleteAndAwait(v2Key, ProcessIntent.DELETED);
    startEventType.trigger(engine, processId);

    // then - v1 is the latest active version and holds the start event
    assertThat(startedByTrigger(v1Key)).describedAs("v1 is started by the trigger").isTrue();
    assertNotStartedByTrigger(Set.of(v2Key, v3Key));
  }

  @Test
  public void shouldDeployAfterDeletingVersionThatTookOverStartEvent() {
    // given - v3 is draining, so v2 took over the start event
    final var processId = Strings.newRandomValidBpmnId();
    deploy(processId, "task-v1");
    final long v2Key = deploy(processId, "task-v2");
    final long v3Key = deploy(processId, "task-v3");
    createInstanceAndAwaitJob(processId, 3);
    deleteAndAwait(v3Key, ProcessIntent.DRAINING);
    deleteAndAwait(v2Key, ProcessIntent.DELETED);

    // when - a timer left on the deleted v2 used to reject every later deployment
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId + "-other").startEvent().endEvent().done())
            .deploy();

    // then
    assertThat(deployment.getIntent()).isEqualTo(DeploymentIntent.CREATED);
  }

  @Test
  public void shouldOnlyHoldStartEventOnNewVersionWhenDeployingWhileLatestDrains() {
    // given - v2 is draining, so v1 took over the start event
    final var processId = Strings.newRandomValidBpmnId();
    final long v1Key = deploy(processId, "task-v1");
    final long v2Key = deploy(processId, "task-v2");
    createInstanceAndAwaitJob(processId, 2);
    deleteAndAwait(v2Key, ProcessIntent.DRAINING);

    // when
    final long v3Key = deploy(processId, "task-v3");
    startEventType.trigger(engine, processId);

    // then - only the new version holds the start event
    assertThat(startedByTrigger(v3Key)).describedAs("v3 is started by the trigger").isTrue();
    assertNotStartedByTrigger(Set.of(v1Key, v2Key));
  }

  // A none start event creates the instances that keep a version draining. Each version gets a
  // distinct task id so every deployment creates a new version.
  private long deploy(final String processId, final String taskId) {
    final var builder =
        Bpmn.createExecutableProcess(processId)
            .startEvent(NONE_START_ID)
            .serviceTask(taskId, t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .moveToProcess(processId)
            .startEvent(TRIGGERED_START_ID);
    startEventType.configure(builder, processId);
    return engine
        .deployment()
        .withXmlResource(
            builder.serviceTask(taskId + "-triggered", t -> t.zeebeJobType(JOB_TYPE)).done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private void createInstanceAndAwaitJob(final String processId, final int version) {
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withVersion(version).create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
  }

  private void deleteAndAwait(final long processDefinitionKey, final ProcessIntent intent) {
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();
    RecordingExporter.processRecords()
        .withIntent(intent)
        .withProcessDefinitionKey(processDefinitionKey)
        .await();
  }

  private boolean startedByTrigger(final long processDefinitionKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessDefinitionKey(processDefinitionKey)
        .withElementId(TRIGGERED_START_ID)
        .exists();
  }

  private void assertNotStartedByTrigger(final Set<Long> processDefinitionKeys) {
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.processInstanceRecords(
                            ProcessInstanceIntent.ELEMENT_ACTIVATED)
                        .withElementId(TRIGGERED_START_ID)
                        .filter(
                            r ->
                                processDefinitionKeys.contains(
                                    r.getValue().getProcessDefinitionKey()))
                        .exists()))
        .describedAs("only the latest active version holds the start event")
        .isFalse();
  }

  private enum StartEventType {
    TIMER {
      @Override
      void configure(final StartEventBuilder start, final String name) {
        start.timerWithCycle("R/PT1M");
      }

      @Override
      void trigger(final EngineRule engine, final String name) {
        engine.increaseTime(Duration.ofMinutes(2));
      }
    },
    MESSAGE {
      @Override
      void configure(final StartEventBuilder start, final String name) {
        start.message(name + "-message");
      }

      @Override
      void trigger(final EngineRule engine, final String name) {
        engine.message().withName(name + "-message").withCorrelationKey("key").publish();
      }
    },
    SIGNAL {
      @Override
      void configure(final StartEventBuilder start, final String name) {
        start.signal(name + "-signal");
      }

      @Override
      void trigger(final EngineRule engine, final String name) {
        engine.signal().withSignalName(name + "-signal").broadcast();
      }
    },
    CONDITIONAL {
      @Override
      void configure(final StartEventBuilder start, final String name) {
        start.condition(c -> c.condition("=x > 0"));
      }

      @Override
      void trigger(final EngineRule engine, final String name) {
        engine.conditionalEvaluation().withVariables(Map.of("x", 1)).evaluate();
      }
    };

    abstract void configure(StartEventBuilder start, String name);

    abstract void trigger(EngineRule engine, String name);
  }
}
