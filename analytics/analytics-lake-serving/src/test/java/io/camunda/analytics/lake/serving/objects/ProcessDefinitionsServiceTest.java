/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.lake.serving.objects.ProcessDefinitionsService.ProcessDefinitionResult;
import io.camunda.analytics.lake.serving.objects.ProcessDefinitionsService.ProcessDefinitionSummary;
import io.camunda.analytics.lake.serving.objects.ProcessDefinitionsService.ProcessDefinitionsListResult;
import io.camunda.analytics.lake.serving.support.ObjectFabricFixtures;
import io.camunda.analytics.lake.serving.support.ParquetFixtures;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code /api/definitions} against one planted {@code process_definitions} row for {@link
 * ObjectFabricFixtures#PROCESS_ID} v1 -- a minimal but valid BPMN 2.0 diagram whose element ids (A,
 * a top-level service task; B, a sub-process; C, nested inside B) match {@link
 * ObjectFabricFixtures}'s own activity fixture exactly, so a later journey-overlay feature can
 * cross-reference the two without a second fixture.
 */
@SpringBootTest
class ProcessDefinitionsServiceTest {

  /**
   * Minimal, valid BPMN 2.0 XML: start -> service task "A" -> sub-process "B" (containing service
   * task "C") -> end. Kept intentionally small; this service treats bpmn_xml as an opaque string,
   * it never parses it.
   */
  static final String BPMN_XML =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                         xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC"
                         xmlns:omgdi="http://www.omg.org/spec/DD/20100524/DI"
                         id="Definitions_1" targetNamespace="http://camunda.org/schema/1.0/bpmn">
        <bpmn:process id="orderProcess" isExecutable="true">
          <bpmn:startEvent id="Start" name="Start">
            <bpmn:outgoing>Flow_1</bpmn:outgoing>
          </bpmn:startEvent>
          <bpmn:serviceTask id="A" name="A">
            <bpmn:incoming>Flow_1</bpmn:incoming>
            <bpmn:outgoing>Flow_2</bpmn:outgoing>
          </bpmn:serviceTask>
          <bpmn:subProcess id="B" name="B">
            <bpmn:incoming>Flow_2</bpmn:incoming>
            <bpmn:outgoing>Flow_3</bpmn:outgoing>
            <bpmn:startEvent id="BStart"/>
            <bpmn:serviceTask id="C" name="C"/>
            <bpmn:endEvent id="BEnd"/>
          </bpmn:subProcess>
          <bpmn:endEvent id="End" name="End">
            <bpmn:incoming>Flow_3</bpmn:incoming>
          </bpmn:endEvent>
          <bpmn:sequenceFlow id="Flow_1" sourceRef="Start" targetRef="A"/>
          <bpmn:sequenceFlow id="Flow_2" sourceRef="A" targetRef="B"/>
          <bpmn:sequenceFlow id="Flow_3" sourceRef="B" targetRef="End"/>
        </bpmn:process>
        <bpmndi:BPMNDiagram id="BPMNDiagram_1">
          <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="orderProcess">
            <bpmndi:BPMNShape id="Start_di" bpmnElement="Start">
              <omgdc:Bounds x="150" y="150" width="36" height="36"/>
            </bpmndi:BPMNShape>
            <bpmndi:BPMNShape id="A_di" bpmnElement="A">
              <omgdc:Bounds x="240" y="128" width="100" height="80"/>
            </bpmndi:BPMNShape>
            <bpmndi:BPMNShape id="B_di" bpmnElement="B">
              <omgdc:Bounds x="390" y="108" width="150" height="120"/>
            </bpmndi:BPMNShape>
            <bpmndi:BPMNShape id="End_di" bpmnElement="End">
              <omgdc:Bounds x="590" y="150" width="36" height="36"/>
            </bpmndi:BPMNShape>
            <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
              <omgdi:waypoint x="186" y="168"/>
              <omgdi:waypoint x="240" y="168"/>
            </bpmndi:BPMNEdge>
            <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2">
              <omgdi:waypoint x="340" y="168"/>
              <omgdi:waypoint x="390" y="168"/>
            </bpmndi:BPMNEdge>
            <bpmndi:BPMNEdge id="Flow_3_di" bpmnElement="Flow_3">
              <omgdi:waypoint x="540" y="168"/>
              <omgdi:waypoint x="590" y="168"/>
            </bpmndi:BPMNEdge>
          </bpmndi:BPMNPlane>
        </bpmndi:BPMNDiagram>
      </bpmn:definitions>
      """;

  /**
   * A second process id, planted alongside {@link ObjectFabricFixtures#PROCESS_ID} for {@code
   * list()}'s grouping/ordering assertions -- sorts after it so {@code ORDER BY process_id} is
   * actually exercised.
   */
  static final String SECOND_PROCESS_ID = "shippingProcess";

  @TempDir private static Path warehouseDir;

  @Autowired private ProcessDefinitionsService processDefinitionsService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "process_definitions",
        // encode(): the lake writer stores bpmn_xml as BINARY (BLOB in the view), so the fixture
        // matches production; the service's typeof guard covers the legacy VARCHAR shape.
        // Three rows: orderProcess v1 (the original fixture, kept for the find() tests below) and
        // v2 (a second version of the same process, deployed later), plus a second process id --
        // together these exercise list()'s per-process grouping, version ordering, latestVersion,
        // and processId ordering.
        "SELECT CAST(5001 AS BIGINT) AS process_definition_key, '"
            + ObjectFabricFixtures.PROCESS_ID
            + "' AS process_id, 1 AS version, 'default' AS tenant_id, encode('"
            + BPMN_XML.replace("'", "''")
            + "') AS bpmn_xml, CAST(TIMESTAMP '2024-01-01 00:00:00' AS TIMESTAMPTZ) AS deployed_at, "
            + "DATE '2024-01-01' AS day"
            + " UNION ALL SELECT CAST(5002 AS BIGINT), '"
            + ObjectFabricFixtures.PROCESS_ID
            + "', 2, 'default', encode('<bpmn:definitions/>'),"
            + " CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ), DATE '2024-02-01'"
            + " UNION ALL SELECT CAST(5003 AS BIGINT), '"
            + SECOND_PROCESS_ID
            + "', 1, 'default', encode('<bpmn:definitions/>'),"
            + " CAST(TIMESTAMP '2024-01-15 00:00:00' AS TIMESTAMPTZ), DATE '2024-01-15'");
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldReturnTheBpmnXmlForAnExistingProcessDefinition() {
    final ProcessDefinitionResult result =
        processDefinitionsService.find(ObjectFabricFixtures.PROCESS_ID, 1);

    assertThat(result.processId()).isEqualTo(ObjectFabricFixtures.PROCESS_ID);
    assertThat(result.version()).isEqualTo(1);
    assertThat(result.bpmnXml()).contains("id=\"A\"", "id=\"B\"", "id=\"C\"");
  }

  @Test
  void shouldReportNotFoundForAVersionThatWasNeverDeployed() {
    assertThatThrownBy(() -> processDefinitionsService.find(ObjectFabricFixtures.PROCESS_ID, 7))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void shouldReportNotFoundForAnUnknownProcessId() {
    assertThatThrownBy(() -> processDefinitionsService.find("no-such-process", 1))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void shouldGroupVersionsByProcessIdOrderedByProcessIdThenVersion() {
    final ProcessDefinitionsListResult result = processDefinitionsService.list();

    assertThat(result.definitions()).hasSize(2);

    final ProcessDefinitionSummary orderProcess = result.definitions().get(0);
    assertThat(orderProcess.processId()).isEqualTo(ObjectFabricFixtures.PROCESS_ID);
    assertThat(orderProcess.latestVersion()).isEqualTo(2);
    assertThat(orderProcess.versions()).extracting("version").containsExactly(1, 2);

    final ProcessDefinitionSummary shippingProcess = result.definitions().get(1);
    assertThat(shippingProcess.processId()).isEqualTo(SECOND_PROCESS_ID);
    assertThat(shippingProcess.latestVersion()).isEqualTo(1);
    assertThat(shippingProcess.versions()).extracting("version").containsExactly(1);
  }

  @Test
  void shouldReturnIsoDeployedAtTimestampsPerVersion() {
    final ProcessDefinitionsListResult result = processDefinitionsService.list();

    final ProcessDefinitionSummary orderProcess = result.definitions().get(0);
    assertThat(orderProcess.versions())
        .extracting("deployedAt")
        .containsExactly("2024-01-01T00:00:00Z", "2024-02-01T00:00:00Z");
  }
}
