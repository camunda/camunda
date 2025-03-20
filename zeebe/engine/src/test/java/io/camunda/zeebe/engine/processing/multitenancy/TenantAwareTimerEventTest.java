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
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class TenantAwareTimerEventTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withSecurityConfig(config -> config.getMultiTenancy().setEnabled(true));

  @Rule public final TestWatcher testWatcher = new RecordingExporterTestWatcher();
  private String processId;

  @Before
  public void setup() {
    processId = Strings.newRandomValidBpmnId();
  }

  @Test
  public void shouldCreateTimerForDefaultTenant() {
    // when
    final var deployed =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("timer-start")
                    .timerWithDuration(Duration.ofMinutes(10))
                    .endEvent()
                    .done())
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .deploy();

    // then
    assertThat(deployed)
        .describedAs("Expect that process with timer was deployed successful")
        .hasIntent(DeploymentIntent.CREATED);

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(
                    deployed.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey())
                .getFirst())
        .describedAs("Expect that timer was created")
        .isNotNull();
  }
}
