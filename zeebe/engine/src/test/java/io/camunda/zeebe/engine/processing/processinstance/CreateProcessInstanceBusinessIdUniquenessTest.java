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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
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
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .deploy();
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
                .withTags("secondInstance")
                .withBpmnProcessId(processId)
                .getFirst())
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            """
            Expected to create instance of process with business id '%s', \
            but an instance with this business id already exists for process definition '%s'"""
                .formatted(businessId, processId));
  }

  @Test
  public void shouldRejectCreateProcessInstanceWithBusinessIdInUseByOtherVersion() {
    final String processId = helper.getBpmnProcessId();
    final String businessId = "biz-123";

    // given - deploy version 1 and create an instance with a business id
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .deploy();
    ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(businessId).create();

    // and - deploy version 2 of the same process
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .userTask("another_task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .deploy();

    // when - try to create an instance with the same business id for version 2
    ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVersion(2)
        .withBusinessId(businessId)
        .withTags("secondInstance")
        .expectRejection()
        .create();

    // then - should be rejected because version 1 already has an active instance with this
    // business id, and uniqueness is enforced across all versions of the same process definition
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .onlyCommandRejections()
                .withTags("secondInstance")
                .withBpmnProcessId(processId)
                .getFirst())
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            """
            Expected to create instance of process with business id '%s', \
            but an instance with this business id already exists for process definition '%s'"""
                .formatted(businessId, processId));
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
                .withTags("secondInstance")
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
                .withTags("secondInstance")
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
                .withTags("secondInstance")
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
                .withTags("secondInstance")
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
                .withTags("secondInstance")
                .getFirst()
                .getValue())
        .hasBusinessId(businessId)
        .hasProcessInstanceKey(secondProcessInstanceKey);
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
    final var parentProcessInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(parentProcessId)
            .withBusinessId(businessId)
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(childProcessId)
                .withParentProcessInstanceKey(parentProcessInstanceKey)
                .limit(2))
        .describedAs("Expect two active child instances with the same business id")
        .allSatisfy(r -> assertThat(r.getValue()).hasBusinessId(businessId));
  }

  @Test
  public void shouldAllowCallActivityToCreateProcessInstanceWithBusinessIdInUse() {
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

    // and an already active instance of the process under test with business id
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processUnderTestId)
            .withBusinessId(businessId)
            .create();
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(processUnderTestId)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect business id in use")
        .hasBusinessId(businessId);

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
        .describedAs("Expect called process instance created even with business id already in use")
        .hasBusinessId(businessId);
  }

  @Test
  public void shouldAllowCreateProcessInstanceWithBusinessIdInUseOnlyByChildInstance() {
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

    // and an instance of process under test created via a call activity with a business id
    final var parentProcessInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processWithCallActivityId)
            .withBusinessId(businessId)
            .create();
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withParentProcessInstanceKey(parentProcessInstanceKey)
                .withBpmnProcessId(processUnderTestId)
                .getFirst()
                .getValue())
        .describedAs("Expect child instance with business id in use")
        .hasBusinessId(businessId);

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processUnderTestId)
            .withBusinessId(businessId)
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withBpmnProcessId(processUnderTestId)
                .getFirst()
                .getValue())
        .describedAs(
            "Allow creating process instance with business id only in use by child instance")
        .hasBusinessId(businessId);
  }

  @Test
  public void shouldCreateProcessInstanceWithBusinessIdOfSourceProcessDefinitionAfterMigration() {
    final String sourceProcessId = helper.getBpmnProcessId() + "_source";
    final String targetProcessId = helper.getBpmnProcessId() + "_target";
    final String businessId = "biz-123";

    // given two process definitions with different process ids
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(sourceProcessId)
                    .startEvent()
                    .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        deployment.getValue().getProcessesMetadata().stream()
            .filter(p -> p.getBpmnProcessId().equals(targetProcessId))
            .findFirst()
            .orElseThrow()
            .getProcessDefinitionKey();

    // and an active process instance with a business id for the source process definition
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(sourceProcessId)
            .withBusinessId(businessId)
            .create();

    // and the process instance is migrated to the target process definition
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("task", "task")
        .migrate();

    // when - create a new process instance with the same business id for the source process
    final var newProcessInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(sourceProcessId)
            .withBusinessId(businessId)
            .withTags("newInstance")
            .create();

    // then - new instance should be created successfully since the business id is no longer
    // associated with the source process definition
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .withIntent(ProcessInstanceCreationIntent.CREATED)
                .withBpmnProcessId(sourceProcessId)
                .withTags("newInstance")
                .getFirst()
                .getValue())
        .hasBusinessId(businessId)
        .hasProcessInstanceKey(newProcessInstanceKey);
  }

  @Test
  public void
      shouldRejectCreateProcessInstanceWithBusinessIdOfTargetProcessDefinitionAfterMigration() {
    final String sourceProcessId = helper.getBpmnProcessId() + "_source";
    final String targetProcessId = helper.getBpmnProcessId() + "_target";
    final String businessId = "biz-123";

    // given two process definitions with different process ids
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(sourceProcessId)
                    .startEvent()
                    .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        deployment.getValue().getProcessesMetadata().stream()
            .filter(p -> p.getBpmnProcessId().equals(targetProcessId))
            .findFirst()
            .orElseThrow()
            .getProcessDefinitionKey();

    // and an active process instance with a business id for the source process definition
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(sourceProcessId)
            .withBusinessId(businessId)
            .create();

    // and the process instance is migrated to the target process definition
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("task", "task")
        .migrate();

    // when - try to create a new process instance with the same business id for the target process
    ENGINE
        .processInstance()
        .ofBpmnProcessId(targetProcessId)
        .withBusinessId(businessId)
        .withTags("rejectedInstance")
        .expectRejection()
        .create();

    // then - should be rejected because the migrated instance now holds the business id
    // for the target process definition
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .onlyCommandRejections()
                .withTags("rejectedInstance")
                .withBpmnProcessId(targetProcessId)
                .getFirst())
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            """
            Expected to create instance of process with business id '%s', \
            but an instance with this business id already exists for process definition '%s'"""
                .formatted(businessId, targetProcessId));
  }

  @Test
  public void
      shouldAllowMigrationWhenTargetProcessDefinitionAlreadyHasActiveInstanceWithSameBusinessId() {
    final String sourceProcessId = helper.getBpmnProcessId() + "_source";
    final String targetProcessId = helper.getBpmnProcessId() + "_target";
    final String businessId = "biz-123";

    // given two process definitions with different process ids
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(sourceProcessId)
                    .startEvent()
                    .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        deployment.getValue().getProcessesMetadata().stream()
            .filter(p -> p.getBpmnProcessId().equals(targetProcessId))
            .findFirst()
            .orElseThrow()
            .getProcessDefinitionKey();

    // and an active process instance with a business id for the target process definition
    ENGINE.processInstance().ofBpmnProcessId(targetProcessId).withBusinessId(businessId).create();

    // and an active process instance with the same business id for the source process definition
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(sourceProcessId)
            .withBusinessId(businessId)
            .create();

    // when - migrate the source instance to the target process definition
    final var event =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("task", "task")
            .migrate();

    // then - migration should succeed because business-id uniqueness is not enforced on migration
    assertThat(event).hasIntent(ProcessInstanceMigrationIntent.MIGRATED);
  }

  @Test
  public void shouldRejectCreateProcessInstanceWithBusinessIdOfOldVersionAfterVersionMigration() {
    final String processId = helper.getBpmnProcessId();
    final String businessId = "biz-123";

    // given - deploy version 1 and create a process instance with a business id
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

    // and - deploy version 2 of the same process
    final long targetProcessDefinitionKey =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                    .userTask("another_task", AbstractUserTaskBuilder::zeebeUserTask)
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst()
            .getProcessDefinitionKey();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("task")
        .await();

    // when - migrate the process instance to version 2
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("task", "task")
        .migrate();

    // then - creating an instance of version 1 with the same business id should be rejected because
    // uniqueness is checked across all versions of a process definition.
    ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVersion(1)
        .withBusinessId(businessId)
        .withTags("rejectedInstance")
        .expectRejection()
        .create();

    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .onlyCommandRejections()
                .withTags("rejectedInstance")
                .withBpmnProcessId(processId)
                .getFirst())
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            """
            Expected to create instance of process with business id '%s', \
            but an instance with this business id already exists for process definition '%s'"""
                .formatted(businessId, processId));
  }

  @Test
  public void shouldAllowVersionMigrationWhenAnotherInstanceOfSameProcessHasSameBusinessId() {
    final String processId = helper.getBpmnProcessId();
    final String businessId = "biz-123";

    // given - deploy version 1 and version 2 of the same process
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .deploy();
    final long v2ProcessDefinitionKey =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                    .userTask("another_task", AbstractUserTaskBuilder::zeebeUserTask)
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst()
            .getProcessDefinitionKey();

    // and - restart the engine with uniqueness disabled so we can create two instances with the
    // same business id for the same process definition
    ENGINE.stop();
    ENGINE.withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(false));
    ENGINE.start();

    final var v1ProcessInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVersion(1)
            .withBusinessId(businessId)
            .create();
    ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVersion(2)
        .withBusinessId(businessId)
        .create();

    // and - restart the engine with uniqueness re-enabled
    ENGINE.stop();
    ENGINE.withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));
    ENGINE.start();

    // when - migrate the v1 instance to v2
    final var event =
        ENGINE
            .processInstance()
            .withInstanceKey(v1ProcessInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(v2ProcessDefinitionKey)
            .addMappingInstruction("task", "task")
            .migrate();

    // then - migration should succeed because business-id uniqueness is not enforced on migration
    assertThat(event).hasIntent(ProcessInstanceMigrationIntent.MIGRATED);
  }
}
