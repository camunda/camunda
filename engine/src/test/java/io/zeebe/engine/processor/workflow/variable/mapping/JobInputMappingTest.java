/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.variable.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.test.util.JsonUtil;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JobInputMappingTest {

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

  @Parameters(name = "from {0} to {2}")
  public static Object[][] parameters() {
    return new Object[][] {
      {"{}", mapping(b -> {}), "{}"},
      {"{'x': 1, 'y': 2}", mapping(b -> {}), "{'x': 1, 'y': 2}"},
      {"{'x': {'y': 2}}", mapping(b -> {}), "{'x': {'y': 2}}"},
      {"{'x': 1}", mapping(b -> b.zeebeInput("x", "y")), "{'x': 1, 'y': 1}"},
      {
        "{'x': 1}",
        mapping(b -> b.zeebeInput("x", "y").zeebeInput("x", "z")),
        "{'x': 1, 'y': 1, 'z': 1}"
      },
      {"{'x': {'y': 2}}", mapping(b -> b.zeebeInput("x.y", "y")), "{'x': {'y': 2}, 'y': 2}"},
    };
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
                      builder.zeebeTaskType("test");
                      mappings.accept(builder);
                    })
                .endEvent()
                .done())
        .deploy()
        .getValue()
        .getDeployedWorkflows()
        .get(0)
        .getWorkflowKey();

    // when
    final long workflowInstanceKey =
        ENGINE_RULE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(initialVariables)
            .create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .await();
    ENGINE_RULE.jobs().withType("test").activate();

    // then
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    assertThat(jobCreated.getValue().getVariables())
        .isEqualTo(JsonUtil.fromJsonAsMap(expectedVariables));
  }

  private static Consumer<ServiceTaskBuilder> mapping(Consumer<ServiceTaskBuilder> mappingBuilder) {
    return mappingBuilder;
  }
}
