/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.multitenancy;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class TenantAwareUpdateVariablesTest {

  public static final String USERNAME = UUID.randomUUID().toString();
  public static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          USERNAME,
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withIdentitySetup()
          .withSecurityConfig(config -> config.getMultiTenancy().setEnabled(true))
          .withSecurityConfig(config -> config.getInitialization().setUsers(List.of(DEFAULT_USER)));

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldUpdateVariablesForDefaultTenant() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .endEvent()
                .done())
        .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("process")
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .create();

    // when
    final var updated =
        ENGINE
            .variables()
            .ofScope(processInstanceKey)
            .forAuthorizedTenants(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .withDocument(Map.of("foo", "bar"))
            .update(USERNAME);

    // then
    assertThat(updated)
        .describedAs("Expect that update was successful")
        .hasIntent(VariableDocumentIntent.UPDATED);
  }

  @Test
  public void shouldRejectUpdateVariablesForUnauthorizedTenant() {
    // given
    final var tenantId = "another-tenant";
    final var username = "username";
    final var user = ENGINE.user().newUser(username).create().getValue();
    final var tenantKey =
        ENGINE.tenant().newTenant().withTenantId(tenantId).create().getValue().getTenantKey();
    ENGINE
        .tenant()
        .addEntity(tenantId)
        .withEntityType(EntityType.USER)
        .withEntityId(username)
        .add();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .endEvent()
                .done())
        .withTenantId("custom-tenant")
        .deploy();

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("process").withTenantId("custom-tenant").create();

    // when
    final var rejection =
        ENGINE
            .variables()
            .ofScope(processInstanceKey)
            .withDocument(Map.of("foo", "bar"))
            .expectRejection()
            .update(user.getUsername());

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update variables for element with key '%s', but no such element was found"
                .formatted(processInstanceKey));
  }

  @Test
  public void shouldUpdateVariablesForSpecificTenant() {
    // given
    final var tenantId = "custom-tenant";
    ENGINE.tenant().newTenant().withTenantId(tenantId).create();
    ENGINE
        .tenant()
        .addEntity(tenantId)
        .withEntityId(USERNAME)
        .withEntityType(EntityType.USER)
        .add();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .endEvent()
                .done())
        .withTenantId(tenantId)
        .deploy();

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("process").withTenantId(tenantId).create();

    // when
    final var updated =
        ENGINE
            .variables()
            .ofScope(processInstanceKey)
            .forAuthorizedTenants(tenantId)
            .withDocument(Map.of("foo", "bar"))
            .update(USERNAME);

    // then
    assertThat(updated)
        .describedAs("Expect that update was successful")
        .hasIntent(VariableDocumentIntent.UPDATED);
  }
}
