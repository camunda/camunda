/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.multitenancy;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class TenantAwareUpdateVariablesTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

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
            .update();

    // then
    assertThat(updated)
        .describedAs("Expect that update was successful")
        .hasIntent(VariableDocumentIntent.UPDATED);
  }

  @Test
  public void shouldUpdateVariablesForSpecificTenant() {
    // given
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
    final var updated =
        ENGINE
            .variables()
            .ofScope(processInstanceKey)
            .forAuthorizedTenants("custom-tenant")
            .withDocument(Map.of("foo", "bar"))
            .update();

    // then
    assertThat(updated)
        .describedAs("Expect that update was successful")
        .hasIntent(VariableDocumentIntent.UPDATED);
  }
}
