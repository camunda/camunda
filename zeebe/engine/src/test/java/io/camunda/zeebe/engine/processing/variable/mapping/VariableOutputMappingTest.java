/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Parameterized tests for variable output mapping using path-based expression merging.
 *
 * <p>Each test deploys a process with an embedded subprocess that completes immediately
 * (start→end). Output mappings on the subprocess map source expressions to target paths. The engine
 * deep-merges the result into the parent scope, preserving unmapped sibling keys.
 */
@RunWith(Parameterized.class)
public final class VariableOutputMappingTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final AtomicInteger COUNTER = new AtomicInteger();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameterized.Parameter() public String testName;

  @Parameterized.Parameter(1)
  public String[][] outputMappings;

  @Parameterized.Parameter(2)
  public String processVariables;

  @Parameterized.Parameter(3)
  public String expectedVarName;

  @Parameterized.Parameter(4)
  public String expectedVarValue;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return List.of(

        // ---- Flat output mappings ----
        new Object[] {
          "should map flat variable to new name",
          new String[][] {{"a", "b"}},
          "{\"a\": 42}",
          "b",
          "42"
        },
        new Object[] {
          "should map flat variable to same name",
          new String[][] {{"b", "a"}},
          "{\"a\": 1, \"b\": 99}",
          "a",
          "99"
        },
        new Object[] {
          "should map multiple flat variables (check first)",
          new String[][] {{"a", "c"}, {"b", "d"}},
          "{\"a\": 10, \"b\": 20}",
          "c",
          "10"
        },
        new Object[] {
          "should map multiple flat variables (check second)",
          new String[][] {{"a", "c"}, {"b", "d"}},
          "{\"a\": 10, \"b\": 20}",
          "d",
          "20"
        },
        new Object[] {
          "should map flat variable containing object value",
          new String[][] {{"a", "b"}},
          "{\"a\": {\"c\": 1, \"d\": 2}}",
          "b",
          "{\"c\":1,\"d\":2}"
        },
        new Object[] {
          "should map computed expression to flat target",
          new String[][] {{"a + b", "c"}},
          "{\"a\": 7, \"b\": 3}",
          "c",
          "10"
        },
        new Object[] {
          "should map flat string variable",
          new String[][] {{"a", "b"}},
          "{\"a\": \"c\"}",
          "b",
          "\"c\""
        },
        new Object[] {
          "should map flat boolean variable",
          new String[][] {{"a", "b"}},
          "{\"a\": true}",
          "b",
          "true"
        },
        new Object[] {
          "should map flat list variable",
          new String[][] {{"a", "b"}},
          "{\"a\": [1, 2, 3]}",
          "b",
          "[1,2,3]"
        },

        // ---- Nested output mappings ----

        new Object[] {
          "should preserve unmapped sibling keys in nested output",
          new String[][] {{"e", "a.b.c"}, {"f", "a.d.c"}},
          "{\"a\": {\"g\": {\"c\": 1, \"d\": 2}, \"b\": {\"c\": \"old\"}, \"d\": {\"c\": []}},"
              + " \"e\": \"new\", \"f\": [\"h\"]}",
          "a",
          "{\"g\":{\"c\":1,\"d\":2},\"b\":{\"c\":\"new\"},\"d\":{\"c\":[\"h\"]}}"
        },
        new Object[] {
          "should preserve all siblings when only one nested key is mapped",
          new String[][] {{"d", "a.c"}},
          "{\"a\": {\"b\": 1, \"c\": 2}, \"d\": 42}",
          "a",
          "{\"b\":1,\"c\":42}"
        },
        new Object[] {
          "should preserve sibling keys at three levels deep",
          new String[][] {{"e", "a.b.c.d"}},
          "{\"a\": {\"b\": {\"c\": {\"d\": \"old\", \"f\": \"keep\"}, \"g\": \"untouched\"}},"
              + " \"e\": \"new\"}",
          "a",
          "{\"b\":{\"c\":{\"d\":\"new\",\"f\":\"keep\"},\"g\":\"untouched\"}}"
        },
        new Object[] {
          "should preserve unmapped keys across multiple top-level variables (check a)",
          new String[][] {{"e", "a.b"}, {"f", "c.d"}},
          "{\"a\": {\"b\": 1, \"g\": 2}, \"c\": {\"d\": 3, \"h\": 4}, \"e\": 10, \"f\": 20}",
          "a",
          "{\"b\":10,\"g\":2}"
        },
        new Object[] {
          "should preserve unmapped keys across multiple top-level variables (check c)",
          new String[][] {{"e", "a.b"}, {"f", "c.d"}},
          "{\"a\": {\"b\": 1, \"g\": 2}, \"c\": {\"d\": 3, \"h\": 4}, \"e\": 10, \"f\": 20}",
          "c",
          "{\"d\":20,\"h\":4}"
        },
        new Object[] {
          "should apply computed expression to nested target and preserve siblings",
          new String[][] {{"a + b", "c.d"}},
          "{\"c\": {\"d\": 0, \"e\": 1}, \"a\": 10, \"b\": 20}",
          "c",
          "{\"d\":30,\"e\":1}"
        },
        new Object[] {
          "should create nested structure when parent variable does not exist",
          new String[][] {{"c", "a.b.d"}},
          "{\"c\": \"e\"}",
          "a",
          "{\"b\":{\"d\":\"e\"}}"
        },
        new Object[] {
          "should map list value into nested target and preserve siblings",
          new String[][] {{"d", "a.b"}},
          "{\"a\": {\"b\": [1], \"c\": 2}, \"d\": [1, 2, 3]}",
          "a",
          "{\"b\":[1,2,3],\"c\":2}"
        },
        new Object[] {
          "should handle mixed nesting depth mappings",
          new String[][] {{"e", "a.b"}, {"f", "a.c.d"}},
          "{\"a\": {\"b\": 0, \"c\": {\"d\": 0, \"g\": 1}, \"h\": 2}, \"e\": 10, \"f\": 20}",
          "a",
          "{\"b\":10,\"c\":{\"d\":20,\"g\":1},\"h\":2}"
        },
        new Object[] {
          "should preserve unmapped branches when multiple branches mapped",
          new String[][] {{"g", "a.b.c"}, {"h", "a.b.d"}, {"i", "a.e.f"}},
          "{\"a\": {\"b\": {\"c\": 0, \"d\": 0, \"j\": 9}, \"e\": {\"f\": []}, \"k\": {\"l\": 1}},"
              + " \"g\": 1, \"h\": 2, \"i\": [3]}",
          "a",
          "{\"b\":{\"c\":1,\"d\":2,\"j\":9},\"e\":{\"f\":[3]},\"k\":{\"l\":1}}"
        },
        new Object[] {
          "should handle boolean values in nested mapping",
          new String[][] {{"c", "a.b"}},
          "{\"a\": {\"b\": false, \"d\": true}, \"c\": true}",
          "a",
          "{\"b\":true,\"d\":true}"
        },
        new Object[] {
          "should map from source variable to nested target and preserve siblings",
          new String[][] {{"c", "a.b"}},
          "{\"a\": {\"b\": 1, \"d\": 2}, \"c\": 99}",
          "a",
          "{\"b\":99,\"d\":2}"
        },
        new Object[] {
          "should map string value into nested target preserving other keys",
          new String[][] {{"c", "a.b"}},
          "{\"a\": {\"b\": \"old\", \"d\": \"keep\"}, \"c\": \"new\"}",
          "a",
          "{\"b\":\"new\",\"d\":\"keep\"}"
        });
  }

  @Test
  public void shouldApplyOutputMappings() throws Exception {
    // given
    final String processId = "proc" + COUNTER.incrementAndGet();
    final BpmnModelInstance process = buildProcess(processId, outputMappings);

    ENGINE_RULE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        ENGINE_RULE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(processVariables)
            .create();

    // when/then
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .limit(1)
        .await();

    final boolean isNewVariable = !OBJECT_MAPPER.readTree(processVariables).has(expectedVarName);
    final var intent = isNewVariable ? VariableIntent.CREATED : VariableIntent.UPDATED;

    final String actualJson =
        RecordingExporter.variableRecords(intent)
            .withProcessInstanceKey(processInstanceKey)
            .withName(expectedVarName)
            .getFirst()
            .getValue()
            .getValue();

    assertThat(OBJECT_MAPPER.readTree(actualJson))
        .isEqualTo(OBJECT_MAPPER.readTree(expectedVarValue));
  }

  private static BpmnModelInstance buildProcess(final String processId, final String[][] mappings) {
    final var sub = Bpmn.createExecutableProcess(processId).startEvent().subProcess("sub");
    for (final String[] mapping : mappings) {
      sub.zeebeOutputExpression(mapping[0], mapping[1]);
    }
    return sub.embeddedSubProcess().startEvent().endEvent().subProcessDone().endEvent().done();
  }
}
