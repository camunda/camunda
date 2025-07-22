/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.multitenancy;

import static org.assertj.core.api.Assertions.entry;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.collection.Maps;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class TenantAwareProcessInstanceVariableTest {

  public static final String USERNAME = UUID.randomUUID().toString();
  public static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          USERNAME,
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  @ClassRule
  public static final EngineRule ENGINE_RULE =
      EngineRule.singlePartition()
          .withIdentitySetup()
          .withSecurityConfig(config -> config.getMultiTenancy().setEnabled(true))
          .withSecurityConfig(config -> config.getInitialization().setUsers(List.of(DEFAULT_USER)));

  public static final String PROCESS_ID = "process";
  private static final String TENANT_ID = "foo";

  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType("test"))
          .endEvent()
          .done();

  private long processDefinitionKey;

  @BeforeClass
  public static void beforeClass() {
    ENGINE_RULE.tenant().newTenant().withTenantId(TENANT_ID).create();
    ENGINE_RULE
        .tenant()
        .addEntity(TENANT_ID)
        .withEntityId(USERNAME)
        .withEntityType(EntityType.USER)
        .add();
  }

  @Before
  public void init() {
    processDefinitionKey =
        ENGINE_RULE
            .deployment()
            .withXmlResource(PROCESS)
            .withTenantId(TENANT_ID)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0)
            .getProcessDefinitionKey();
  }

  @Test
  public void shouldAssignTenantOnVariableCreation() {
    // when
    final long processInstanceKey =
        ENGINE_RULE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables("{'x':1}")
            .withTenantId(TENANT_ID)
            .create();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(processInstanceKey)
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasTenantId(TENANT_ID)
        .hasName("x")
        .hasValue("1");
  }

  @Test
  public void shouldAssignTenantOnVariableUpdate() {
    // given
    final long processInstanceKey =
        ENGINE_RULE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables("{'x':1}")
            .withTenantId(TENANT_ID)
            .create();

    // when
    ENGINE_RULE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Maps.of(entry("x", 2)))
        .forAuthorizedTenants(TENANT_ID)
        .update(USERNAME);

    // then
    final Record<VariableRecordValue> variableUpdated =
        RecordingExporter.variableRecords(VariableIntent.UPDATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(variableUpdated.getValue())
        .hasScopeKey(processInstanceKey)
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasTenantId(TENANT_ID)
        .hasName("x")
        .hasValue("2");
  }
}
