/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class GlobalVariableCreateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  @ClassRule public static final EngineRule ENGINE_MULTI = EngineRule.multiplePartition(3);
  private static final String PROCESS_ID = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String jobType;

  @Before
  public void before() {
    jobType = Strings.newRandomValidBpmnId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType(jobType))
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();
  }

  @Test
  public void shouldSetGlobalVariable() {
    final Record<VariableRecordValue> result =
        ENGINE.globalVariable().withDocument(Map.of("MY_KEY", "Hello")).create();

    final Record<VariableRecordValue> createdVariable =
        RecordingExporter.variableRecords(VariableIntent.CREATED).getFirst();

    Assertions.assertThat(createdVariable.getValue())
        .hasName("MY_KEY")
        .hasValue("\"Hello\"")
        .hasScopeKey(-1);
  }

  @Test
  public void shouldSetGlobalVariableMultiPartition() {
    final Record<VariableRecordValue> result =
        ENGINE_MULTI.globalVariable().withDocument(Map.of("MY_KEY", "Hello")).create();

    for (int i = 1; i <= 2; i++) {

      System.out.println("Partition " + i);
      final Record<VariableRecordValue> createdVariable =
          RecordingExporter.records()
              .withPartitionId(i)
              .variableRecords()
              .withScopeKey(-1)
              .getFirst();

      Assertions.assertThat(createdVariable.getValue())
          .hasName("MY_KEY")
          .hasValue("\"Hello\"")
          .hasScopeKey(-1);
    }
  }
}
