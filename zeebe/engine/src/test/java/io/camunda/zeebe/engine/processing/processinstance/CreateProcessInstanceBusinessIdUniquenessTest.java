/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for process instance creation with business ID uniqueness enforcement enabled. When {@code
 * businessIdUniquenessEnabled} is set to {@code true}, creating a process instance with a business
 * ID that is already in use by an active process instance will be rejected.
 */
public final class CreateProcessInstanceBusinessIdUniquenessTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldRejectCreateProcessInstanceWithBusinessIdInUse() {
    final String processId = helper.getBpmnProcessId();
    final String businessId = "biz-123";

    // given
    final var processDefinitionKey =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst()
            .getProcessDefinitionKey();
    ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(businessId).create();

    // when
    ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withBusinessId(businessId)
        .withTags("secondInstance")
        .expectRejection()
        .create();

    // then
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .onlyCommandRejections()
                .valueFilter(r -> r.getTags().contains("secondInstance"))
                .withBpmnProcessId(processId)
                .getFirst())
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            """
            Expected to create instance of process with business id '%s', \
            but an instance with this business id already exists for process definition key '%s'"""
                .formatted(businessId, processDefinitionKey));
  }

  @Test
  public void shouldCreateProcessInstanceWithBusinessIdInUseForOtherTenant() {
    final String processId = helper.getBpmnProcessId();
    final String businessId = "biz-123";

    // given
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
            .endEvent()
            .done();
    ENGINE.deployment().withTenantId("tenant_one").withXmlResource(process).deploy();
    ENGINE.deployment().withTenantId("tenant_two").withXmlResource(process).deploy();
    ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withBusinessId(businessId)
        .withTenantId("tenant_one")
        .create();

    // when
    final var secondProcessInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withBusinessId(businessId)
            .withTenantId("tenant_two")
            .withTags("secondInstance")
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .withIntent(ProcessInstanceCreationIntent.CREATED)
                .withBpmnProcessId(processId)
                .valueFilter(r -> r.getTags().contains("secondInstance"))
                .getFirst()
                .getValue())
        .hasBusinessId(businessId)
        .hasTenantId("tenant_two")
        .hasProcessInstanceKey(secondProcessInstanceKey);
  }

  @Test
  public void shouldAllowSameBusinessIdForDifferentProcessDefinitions() {
    final String processId1 = helper.getBpmnProcessId() + "_1";
    final String processId2 = helper.getBpmnProcessId() + "_2";
    final String businessId = "shared-biz-id";

    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId1)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .deploy();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId2)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .deploy();

    // create first process instance with business id
    ENGINE.processInstance().ofBpmnProcessId(processId1).withBusinessId(businessId).create();

    // when - create second process instance with same business id but different process definition
    final var secondInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId2)
            .withBusinessId(businessId)
            .withTags("secondInstance")
            .create();

    // then - second instance should be created successfully
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .withIntent(ProcessInstanceCreationIntent.CREATED)
                .withBpmnProcessId(processId2)
                .valueFilter(r -> r.getTags().contains("secondInstance"))
                .getFirst()
                .getValue())
        .hasBusinessId(businessId)
        .hasProcessInstanceKey(secondInstanceKey);
  }

  @Test
  public void shouldCreateProcessInstanceWithBusinessIdNoLongerInUseDueToCompletion() {
    final String processId = helper.getBpmnProcessId();
    final String businessId = "biz-123";

    // given a process instance with a business id that has completed
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(businessId).create();
    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // when
    final var secondProcessInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withBusinessId(businessId)
            .withTags("secondInstance")
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .withIntent(ProcessInstanceCreationIntent.CREATED)
                .withBpmnProcessId(processId)
                .valueFilter(r -> r.getTags().contains("secondInstance"))
                .getFirst()
                .getValue())
        .hasBusinessId(businessId)
        .hasProcessInstanceKey(secondProcessInstanceKey);
  }

  @Test
  public void shouldCreateProcessInstanceWithBusinessIdNoLongerInUseDueToCancellation() {
    final String processId = helper.getBpmnProcessId();
    final String businessId = "biz-123";

    // given a process instance with a business id that has terminated
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(businessId).create();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // when
    final var secondProcessInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withBusinessId(businessId)
            .withTags("secondInstance")
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .withIntent(ProcessInstanceCreationIntent.CREATED)
                .withBpmnProcessId(processId)
                .valueFilter(r -> r.getTags().contains("secondInstance"))
                .getFirst()
                .getValue())
        .hasBusinessId(businessId)
        .hasProcessInstanceKey(secondProcessInstanceKey);
  }

  @Test
  public void shouldCreateProcessInstanceWithBusinessIdNoLongerInUseDueToBanning() {
    final String processId = helper.getBpmnProcessId();
    final String businessId = "biz-123";

    // given a process instance with a business id that has been banned
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(businessId).create();
    ENGINE.banInstanceInNewTransaction(1, processInstanceKey);
    RecordingExporter.errorRecords().withRecordKey(processInstanceKey).await();

    // when
    final var secondProcessInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withBusinessId(businessId)
            .withTags("secondInstance")
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .withIntent(ProcessInstanceCreationIntent.CREATED)
                .withBpmnProcessId(processId)
                .valueFilter(r -> r.getTags().contains("secondInstance"))
                .getFirst()
                .getValue())
        .hasBusinessId(businessId)
        .hasProcessInstanceKey(secondProcessInstanceKey);
  }

  @Test
  public void shouldInheritBusinessIdFromParentProcessInstance() {
    final String processUnderTestId = helper.getBpmnProcessId() + "_underTest";
    final String processWithCallActivityId = helper.getBpmnProcessId() + "_callActivity";
    final String businessId = "biz-123";

    // given:
    // - two processes: process under test and call activity process
    // - and an instance of the process under test with a business id
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processUnderTestId)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .withXmlResource(
            Bpmn.createExecutableProcess(processWithCallActivityId)
                .startEvent()
                .callActivity("call", c -> c.zeebeProcessId(processUnderTestId))
                .endEvent()
                .done())
        .deploy();

    // when
    final var parentProcessInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processWithCallActivityId)
            .withBusinessId(businessId)
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(processUnderTestId)
                .withParentProcessInstanceKey(parentProcessInstanceKey)
                .getFirst()
                .getValue())
        .describedAs(
            "Expect the called process instance to inherit the business id from the call activity instance")
        .hasBusinessId(businessId);
  }

  @Test
  public void shouldAllowMultipleChildProcessInstancesWithSameBusinessId() {
    final String parentProcessId = helper.getBpmnProcessId() + "_parent";
    final String childProcessId = helper.getBpmnProcessId() + "_child";
    final String businessId = "biz-123";

    // given two processes:
    // - parent process with a parallel gateway that creates two child instances in parallel
    // - child process with a user task
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(parentProcessId)
                .startEvent()
                .parallelGateway("fork")
                .callActivity("call1", c -> c.zeebeProcessId(childProcessId))
                .endEvent()
                .moveToLastGateway()
                .callActivity("call2", c -> c.zeebeProcessId(childProcessId))
                .endEvent()
                .done())
        .withXmlResource(
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .deploy();

    // when
    final var parentInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(parentProcessId)
            .withBusinessId(businessId)
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(childProcessId)
                .withParentProcessInstanceKey(parentInstanceKey)
                .limit(2))
        .describedAs("Expect two active child instances with the same business id")
        .allSatisfy(r -> assertThat(r.getValue()).hasBusinessId(businessId));
  }
}
