/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.JobWorkerElementBuilder;
import io.camunda.zeebe.engine.util.JobWorkerElementBuilderProvider;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ZeebeJobWorkerElementBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Collection;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Verifies the multi-tenant behavior of elements that are based on jobs and should be processed by
 * job workers, ex. service tasks. See {@link JobWorkerElementTest} for the non-multi-tenant tests.
 */
@RunWith(Parameterized.class)
public class JobWorkerElementMultiTenantTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withSecurityConfig(
              config -> {
                config.getAuthorizations().setEnabled(true);
                config.getMultiTenancy().setChecksEnabled(true);
              });

  private static final String PROCESS_ID = "process";
  private static String tenantId;
  private static String username;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter public JobWorkerElementBuilder elementBuilder;

  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return JobWorkerElementBuilderProvider.buildersAsParameters();
  }

  private BpmnModelInstance process(
      final Consumer<ZeebeJobWorkerElementBuilder<?>> elementModifier) {
    final var processBuilder = Bpmn.createExecutableProcess(PROCESS_ID).startEvent();

    final var jobWorkerElementBuilder = elementBuilder.build(processBuilder, elementModifier);
    return jobWorkerElementBuilder.id("task").done();
  }

  @Before
  public void setUp() {
    tenantId = Strings.newRandomValidIdentityId();
    username = Strings.newRandomValidIdentityId();
    ENGINE.tenant().newTenant().withTenantId(tenantId).create();
    ENGINE.user().newUser(username).create();
    ENGINE
        .tenant()
        .addEntity(tenantId)
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeJobType("test")))
        .withTenantId(tenantId)
        .deploy();

    ENGINE
        .authorization()
        .newAuthorization()
        .withPermissions(PermissionType.UPDATE_PROCESS_INSTANCE)
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId(PROCESS_ID)
        .withResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
        .withOwnerId(username)
        .withOwnerType(AuthorizationOwnerType.USER)
        .create();
  }

  @Test
  public void shouldActivateTaskWithCustomTenant() {
    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(tenantId).create();

    // then
    final io.camunda.zeebe.protocol.record.Record<ProcessInstanceRecordValue> taskActivating =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withTenantId(tenantId)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(elementBuilder.getElementType())
            .getFirst();

    Assertions.assertThat(taskActivating.getValue())
        .hasElementId("task")
        .hasBpmnElementType(elementBuilder.getElementType())
        .hasFlowScopeKey(processInstanceKey)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey)
        .hasTenantId(tenantId);
  }

  @Test
  public void shouldCompleteTaskWithCustomTenant() {
    // given
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(tenantId).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("test")
        .withAuthorizedTenantIds(tenantId)
        .complete(username);

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withTenantId(tenantId)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(elementBuilder.getElementType(), ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(elementBuilder.getElementType(), ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }
}
