/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.variable.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.JsonUtil;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class JobInputMappingTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter(0)
  public String initialVariables;

  @Parameter(1)
  public Consumer<ServiceTaskBuilder> mappings;

  @Parameter(2)
  public String expectedVariables;

  private String jobType;

  @Parameters(name = "from {0} to {2}")
  public static Object[][] parameters() {
    return new Object[][] {
      {"{}", mapping(b -> {}), "{}"},
      {"{'x': 1, 'y': 2}", mapping(b -> {}), "{'x': 1, 'y': 2}"},
      {"{'x': {'y': 2}}", mapping(b -> {}), "{'x': {'y': 2}}"},
      {"{'x': 1}", mapping(b -> b.zeebeInputExpression("x", "y")), "{'x': 1, 'y': 1}"},
      {
        "{'x': 1}",
        mapping(b -> b.zeebeInputExpression("x", "y").zeebeInputExpression("x", "z")),
        "{'x': 1, 'y': 1, 'z': 1}"
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> b.zeebeInputExpression("x.y", "y")),
        "{'x': {'y': 2}, 'y': 2}"
      },
    };
  }

  @Before
  public void setup() {
    jobType = Strings.newRandomValidBpmnId();
  }

  @Test
  public void shouldApplyInputMappings() {
    // given
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    "service",
                    builder -> {
                      builder.zeebeJobType(jobType);
                      mappings.accept(builder);
                    })
                .endEvent()
                .done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .get(0)
        .getProcessDefinitionKey();

    // when
    final long processInstanceKey =
        ENGINE_RULE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(initialVariables)
            .create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE_RULE.jobs().withType(jobType).activate();

    // then
    final var jobRecordValue =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED)
            .withType(jobType)
            .getFirst()
            .getValue()
            .getJobs()
            .get(0);

    assertThat(jobRecordValue.getVariables()).isEqualTo(JsonUtil.fromJsonAsMap(expectedVariables));
  }

  private static Consumer<ServiceTaskBuilder> mapping(
      final Consumer<ServiceTaskBuilder> mappingBuilder) {
    return mappingBuilder;
  }
}
