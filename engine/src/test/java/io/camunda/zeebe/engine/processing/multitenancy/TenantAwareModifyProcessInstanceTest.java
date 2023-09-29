/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.multitenancy;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class TenantAwareModifyProcessInstanceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldModifyInstanceForDefaultTenant() {
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
    final var modified =
        ENGINE.processInstance().withInstanceKey(processInstanceKey).modification().modify();

    // then
    assertThat(modified)
        .describedAs("Expect that modification was successful")
        .hasIntent(ProcessInstanceModificationIntent.MODIFIED);
  }

  @Test
  public void shouldRejectModifyInstanceForSpecificTenant() {
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
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
            Expected to modify process instance but process instance belongs to tenant \
            'custom-tenant' while modification is not yet supported with multi-tenancy. \
            Only process instances belonging to the default tenant '<default>' can be modified. \
            See https://github.com/camunda/zeebe/issues/13288 for more details.""");
  }
}
