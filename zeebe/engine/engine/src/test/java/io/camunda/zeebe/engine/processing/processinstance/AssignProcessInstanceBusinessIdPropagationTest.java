/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies the forward-only propagation of a late-assigned Business ID (ADR 0006, D8): artifacts
 * created after the assignment carry the Business ID, while artifacts that already existed keep an
 * empty one. Covers each artifact type that captures the Business ID at creation.
 */
public final class AssignProcessInstanceBusinessIdPropagationTest {

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(false));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldPropagateToJobsButNotToPreExistingOnes() {
    // given: a job exists before assignment, and one is created after
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("p")
                .startEvent()
                .serviceTask("before", t -> t.zeebeJobType("t-before"))
                .userTask("wait", AbstractUserTaskBuilder::zeebeUserTask)
                .serviceTask("after", t -> t.zeebeJobType("t-after"))
                .endEvent()
                .done())
        .deploy();
    final long pi = engine.processInstance().ofBpmnProcessId("p").create();
    engine.job().ofInstance(pi).withType("t-before").complete();

    // when
    engine
        .processInstance()
        .withInstanceKey(pi)
        .businessIdAssignment()
        .withBusinessId("biz-1")
        .assign();
    engine.userTask().ofInstance(pi).complete();

    // then
    assertThat(businessIdOfJob(pi, "t-before")).describedAs("pre-existing job").isEqualTo("");
    assertThat(businessIdOfJob(pi, "t-after"))
        .describedAs("job after assignment")
        .isEqualTo("biz-1");
  }

  @Test
  public void shouldPropagateToUserTasksButNotToPreExistingOnes() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("p")
                .startEvent()
                .userTask("before", AbstractUserTaskBuilder::zeebeUserTask)
                .userTask("after", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .deploy();
    final long pi = engine.processInstance().ofBpmnProcessId("p").create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(pi)
        .withElementId("before")
        .await();

    // when
    engine
        .processInstance()
        .withInstanceKey(pi)
        .businessIdAssignment()
        .withBusinessId("biz-1")
        .assign();
    engine.userTask().ofInstance(pi).complete();

    // then
    assertThat(businessIdOfUserTask(pi, "before"))
        .describedAs("pre-existing user task")
        .isEqualTo("");
    assertThat(businessIdOfUserTask(pi, "after"))
        .describedAs("user task after assignment")
        .isEqualTo("biz-1");
  }

  @Test
  public void shouldPropagateToMessageSubscriptionsButNotToPreExistingOnes() {
    // given: a boundary message subscription exists before assignment, and an intermediate catch
    // event subscribes after
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("p")
                .startEvent()
                .userTask("wait", AbstractUserTaskBuilder::zeebeUserTask)
                .boundaryEvent(
                    "boundary",
                    b -> b.message(m -> m.name("m-before").zeebeCorrelationKeyExpression("key")))
                .endEvent()
                .moveToActivity("wait")
                .intermediateCatchEvent(
                    "catch",
                    e -> e.message(m -> m.name("m-after").zeebeCorrelationKeyExpression("key")))
                .endEvent()
                .done())
        .deploy();
    final long pi =
        engine.processInstance().ofBpmnProcessId("p").withVariables(Map.of("key", "k1")).create();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(pi)
        .withMessageName("m-before")
        .await();

    // when
    engine
        .processInstance()
        .withInstanceKey(pi)
        .businessIdAssignment()
        .withBusinessId("biz-1")
        .assign();
    engine.userTask().ofInstance(pi).complete();

    // then
    assertThat(businessIdOfSubscription(pi, "m-before"))
        .describedAs("pre-existing message subscription")
        .isEqualTo("");
    assertThat(businessIdOfSubscription(pi, "m-after"))
        .describedAs("message subscription after assignment")
        .isEqualTo("biz-1");
  }

  @Test
  public void shouldPropagateToDecisionsButNotToPreExistingOnes() {
    // given: a decision evaluated before assignment, and one evaluated after
    engine
        .deployment()
        .withXmlClasspathResource("/dmn/decision-table.dmn")
        .withXmlResource(
            Bpmn.createExecutableProcess("p")
                .startEvent()
                .businessRuleTask(
                    "dmn-before",
                    t -> t.zeebeCalledDecisionId("jedi_or_sith").zeebeResultVariable("r1"))
                .userTask("wait", AbstractUserTaskBuilder::zeebeUserTask)
                .businessRuleTask(
                    "dmn-after",
                    t -> t.zeebeCalledDecisionId("jedi_or_sith").zeebeResultVariable("r2"))
                .endEvent()
                .done())
        .deploy();
    final long pi =
        engine
            .processInstance()
            .ofBpmnProcessId("p")
            .withVariables(Map.of("lightsaberColor", "blue"))
            .create();
    RecordingExporter.decisionEvaluationRecords(DecisionEvaluationIntent.EVALUATED)
        .withProcessInstanceKey(pi)
        .withElementId("dmn-before")
        .await();

    // when
    engine
        .processInstance()
        .withInstanceKey(pi)
        .businessIdAssignment()
        .withBusinessId("biz-1")
        .assign();
    engine.userTask().ofInstance(pi).complete();

    // then
    assertThat(businessIdOfDecision(pi, "dmn-before"))
        .describedAs("pre-existing decision evaluation")
        .isEqualTo("");
    assertThat(businessIdOfDecision(pi, "dmn-after"))
        .describedAs("decision evaluation after assignment")
        .isEqualTo("biz-1");
  }

  @Test
  public void shouldPropagateToCallActivityChildrenButNotToPreExistingOnes() {
    // given: a child started before assignment, and one started after
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("child-before").startEvent().endEvent().done())
        .withXmlResource(
            Bpmn.createExecutableProcess("child-after")
                .startEvent()
                .userTask("childTask", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .withXmlResource(
            Bpmn.createExecutableProcess("p")
                .startEvent()
                .callActivity("call-before", c -> c.zeebeProcessId("child-before"))
                .userTask("wait", AbstractUserTaskBuilder::zeebeUserTask)
                .callActivity("call-after", c -> c.zeebeProcessId("child-after"))
                .endEvent()
                .done())
        .deploy();
    final long pi = engine.processInstance().ofBpmnProcessId("p").create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withParentProcessInstanceKey(pi)
        .withBpmnProcessId("child-before")
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // when
    engine
        .processInstance()
        .withInstanceKey(pi)
        .businessIdAssignment()
        .withBusinessId("biz-1")
        .assign();
    engine.userTask().ofInstance(pi).complete();

    // then
    assertThat(businessIdOfChild(pi, "child-before"))
        .describedAs("pre-existing call activity child")
        .isEqualTo("");
    assertThat(businessIdOfChild(pi, "child-after"))
        .describedAs("call activity child after assignment")
        .isEqualTo("biz-1");
  }

  private String businessIdOfJob(final long processInstanceKey, final String jobType) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(jobType)
        .getFirst()
        .getValue()
        .getBusinessId();
  }

  private String businessIdOfUserTask(final long processInstanceKey, final String elementId) {
    return RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(elementId)
        .getFirst()
        .getValue()
        .getBusinessId();
  }

  private String businessIdOfSubscription(final long processInstanceKey, final String messageName) {
    return RecordingExporter.processMessageSubscriptionRecords(
            ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withMessageName(messageName)
        .getFirst()
        .getValue()
        .getBusinessId();
  }

  private String businessIdOfDecision(final long processInstanceKey, final String elementId) {
    return RecordingExporter.decisionEvaluationRecords(DecisionEvaluationIntent.EVALUATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(elementId)
        .getFirst()
        .getValue()
        .getBusinessId();
  }

  private String businessIdOfChild(final long processInstanceKey, final String childProcessId) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withParentProcessInstanceKey(processInstanceKey)
        .withBpmnProcessId(childProcessId)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst()
        .getValue()
        .getBusinessId();
  }
}
