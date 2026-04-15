/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenericResourceDeploymentTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Parameter(0)
  public String resourceName;

  @Parameter(1)
  public String content;

  @Parameters(name = "{0}")
  public static Collection<Object[]> fileTypes() {
    return Arrays.asList(
        new Object[][] {
          {"my-script.txt", "echo 'Hello World'"},
          {"runbook.md", "# Runbook\n\n## Steps\n\n1. Check logs"},
          {"config.yml", "server:\n  port: 8080\n  host: localhost"},
          {"pipeline.yaml", "stages:\n  - name: build\n    script: mvn package"},
          {"settings.json", "{\"timeout\": 30, \"retries\": 3, \"mode\": \"production\"}"},
        });
  }

  @Test
  public void shouldDeployGenericResource() {
    // when
    final var deploymentEvent =
        engine
            .deployment()
            .withJsonResource(content.getBytes(StandardCharsets.UTF_8), resourceName)
            .deploy();

    // then
    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATED)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasRecordType(RecordType.EVENT);
    assertThat(deploymentEvent.getValue().getResourceMetadata())
        .singleElement()
        .satisfies(
            resourceMetadata ->
                Assertions.assertThat(resourceMetadata)
                    .hasResourceId(resourceName)
                    .hasVersion(1)
                    .hasResourceName(resourceName)
                    .isNotDuplicate()
                    .hasDeploymentKey(deploymentEvent.getKey()));
  }
}
