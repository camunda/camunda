/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.LakeConfig;
import io.camunda.analytics.lake.model.ActivityRow;
import io.camunda.analytics.lake.model.InstanceRow;
import io.camunda.analytics.lake.write.IcebergLakeWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end test of the {@code /process-map} endpoints against a real {@link LakeUiServer} HTTP
 * server and a tiny synthetic warehouse: two completed instances of the same process definition,
 * one visiting elements {@code A, B, C} and the other only {@code A, B} (skipping {@code C}).
 * Exercises:
 *
 * <ul>
 *   <li>{@code /api/process-map/catalog} -- the BPMN file (set up under an explicit {@code
 *       lake.bpmnDir} override) matches the data's process id
 *   <li>{@code /api/process-map/model} -- sequence flows parsed out of the BPMN XML
 *   <li>{@code /api/process-map} -- node execution-count aggregates derived straight from the raw
 *       {@code activities} table, with no compaction step required at all
 * </ul>
 */
class ProcessMapEndpointTest {

  private static final long PROCESS_DEFINITION_KEY = 777L;
  private static final String PROCESS_ID = "map-test-process";

  private static final String BPMN_XML =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                        id="defs" targetNamespace="http://camunda.org/test">
        <bpmn:process id="map-test-process" name="Map Test Process" isExecutable="true">
          <bpmn:startEvent id="Start" name="Start"/>
          <bpmn:serviceTask id="A" name="Task A"/>
          <bpmn:serviceTask id="B" name="Task B"/>
          <bpmn:serviceTask id="C" name="Task C"/>
          <bpmn:endEvent id="End" name="End"/>
          <bpmn:sequenceFlow id="Flow_SA" sourceRef="Start" targetRef="A"/>
          <bpmn:sequenceFlow id="Flow_AB" sourceRef="A" targetRef="B"/>
          <bpmn:sequenceFlow id="Flow_BC" sourceRef="B" targetRef="C"/>
          <bpmn:sequenceFlow id="Flow_CE" sourceRef="C" targetRef="End"/>
        </bpmn:process>
      </bpmn:definitions>
      """;

  @Test
  void shouldReturnNodeAggregatesFromRawActivities(@TempDir final Path tempDir) throws Exception {
    // given a mini warehouse: instance 10 runs A, B, C; instance 20 runs only A, B (skips C) --
    // and a matching BPMN model under an explicit lake.bpmnDir override
    final Path warehouseDir = tempDir.resolve("warehouse");
    final Path stateDir = tempDir.resolve("state");
    final Path bpmnDir = tempDir.resolve("bpmn");
    Files.createDirectories(bpmnDir);
    Files.writeString(bpmnDir.resolve("model.bpmn"), BPMN_XML, StandardCharsets.UTF_8);

    final LakeConfig config =
        new LakeConfig(
            "http://localhost:0",
            "test-topic",
            "test-group",
            warehouseDir,
            stateDir,
            1,
            2000L,
            0L,
            0L,
            0,
            null);
    final IcebergLakeWriter writer = new IcebergLakeWriter(config);
    LakeUiServer server = null;
    try {
      writer.append(instanceRow(10L));
      writer.append(activityRow(10L, "A", 1L, 0L, 100L));
      writer.append(activityRow(10L, "B", 2L, 150L, 250L));
      writer.append(activityRow(10L, "C", 3L, 300L, 400L));
      writer.flush(0, 0L);

      writer.append(instanceRow(20L));
      writer.append(activityRow(20L, "A", 4L, 0L, 100L));
      writer.append(activityRow(20L, "B", 5L, 150L, 250L));
      writer.flush(0, 1L);

      // when the UI server is started (port 0 -- OS-assigned, so parallel test runs never collide)
      // against this warehouse, with the BPMN dir explicitly overridden to the temp dir above --
      // no compaction step needed, node stats come straight from activities
      server = new LakeUiServer(0, warehouseDir, stateDir, bpmnDir);
      server.start();
      final int port = server.boundPort();
      final HttpClient http = HttpClient.newHttpClient();
      final String base = "http://127.0.0.1:" + port;

      // then the catalog endpoint matches the BPMN model against the data's process id
      final String catalogJson = get(http, base + "/api/process-map/catalog");
      assertThat(catalogJson).contains("\"processId\":\"" + PROCESS_ID + "\"");
      assertThat(catalogJson).contains("\"dataProcessIds\":[\"" + PROCESS_ID + "\"]");

      // and the model endpoint exposes the parsed sequence flows
      final String modelJson = get(http, base + "/api/process-map/model?process=" + PROCESS_ID);
      assertThat(modelJson).contains("\"sourceRef\":\"A\",\"targetRef\":\"B\"");
      assertThat(modelJson).contains("\"sourceRef\":\"B\",\"targetRef\":\"C\"");

      // and the process-map data endpoint reports both instances' node execution counts
      final String dataJson = get(http, base + "/api/process-map?process=" + PROCESS_ID);
      assertThat(dataJson).contains("\"process\":\"" + PROCESS_ID + "\"");
      assertThat(nodeExecutionCount(dataJson, "A")).isEqualTo(2L);
      assertThat(nodeExecutionCount(dataJson, "B")).isEqualTo(2L);
      assertThat(nodeExecutionCount(dataJson, "C")).isEqualTo(1L);
    } finally {
      if (server != null) {
        server.close();
      }
      writer.close();
    }
  }

  private static String get(final HttpClient http, final String url) throws Exception {
    final HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as("GET " + url).isEqualTo(200);
    return response.body();
  }

  private static Long nodeExecutionCount(final String json, final String elementId) {
    final Matcher matcher =
        Pattern.compile(
                "\\{\"elementId\":\"" + Pattern.quote(elementId) + "\",\"executionCount\":(\\d+)")
            .matcher(json);
    return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
  }

  private static InstanceRow instanceRow(final long instanceKey) {
    final long startMs = instanceKey * 1000L;
    return new InstanceRow(
        instanceKey,
        PROCESS_DEFINITION_KEY,
        PROCESS_ID,
        1,
        "<default>",
        "COMPLETED",
        startMs,
        startMs + 500,
        500,
        "{}");
  }

  private static ActivityRow activityRow(
      final long instanceKey,
      final String elementId,
      final long elementKey,
      final long startOffsetMs,
      final long endOffsetMs) {
    final long instanceStartMs = instanceKey * 1000L;
    return new ActivityRow(
        instanceKey,
        PROCESS_ID,
        1,
        "<default>",
        elementId,
        "SERVICE_TASK",
        elementKey,
        "COMPLETED",
        instanceStartMs + startOffsetMs,
        instanceStartMs + endOffsetMs,
        endOffsetMs - startOffsetMs,
        instanceStartMs);
  }
}
