/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import io.camunda.optimize.dto.optimize.FlowNodeDataDto;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class BpmnModelUtilTest {

  private static final String SIMPLE_BPMN_RESOURCE_PATH = "/test/data/simpleBpmn.xml";

  private String getBpmnXml() throws IOException {
    return BpmnModelUtil.getResourceFileAsString(SIMPLE_BPMN_RESOURCE_PATH);
  }

  @Test
  void testParseBpmnModel() throws IOException {
    BpmnModelInstance modelInstance = BpmnModelUtil.parseBpmnModel(getBpmnXml());
    assertNotNull(modelInstance, "BPMN model should be parsed correctly.");
  }

  @Test
  void testExtractFlowNodeData() throws IOException {
    List<FlowNodeDataDto> flowNodeData = BpmnModelUtil.extractFlowNodeData(getBpmnXml());
    assertNotNull(flowNodeData, "FlowNodeData list should not be null.");
    assertFalse(flowNodeData.isEmpty(), "FlowNodeData list should not be empty.");

    FlowNodeDataDto firstNode = flowNodeData.get(0);
    assertNotNull(firstNode.getId(), "FlowNode ID should not be null.");
    assertNotNull(firstNode.getName(), "FlowNode name should not be null.");
  }

  @Test
  void testExtractUserTaskNames() throws IOException {
    Map<String, String> userTaskNames = BpmnModelUtil.extractUserTaskNames(getBpmnXml());
    assertNotNull(userTaskNames, "UserTask names map should not be null.");
    assertFalse(userTaskNames.isEmpty(), "UserTask names map should not be empty.");

    assertTrue(userTaskNames.containsKey("UserTask_DecideOnApplication"),
        "Map should contain expected UserTask ID.");
    assertEquals("Decide on application", userTaskNames.get("UserTask_DecideOnApplication"),
        "UserTask name should match expected value.");
  }

  @Test
  void testExtractProcessDefinitionName() throws IOException {
    Optional<String> processName = BpmnModelUtil.extractProcessDefinitionName("customer_onboarding_en", getBpmnXml());
    assertTrue(processName.isPresent(), "Process name should be present.");
    assertEquals("Customer Onboarding", processName.get(), "Process name should match expected value.");
  }

  @Test
  void testExtractFlowNodeNames() throws IOException {
    List<FlowNodeDataDto> flowNodeData = BpmnModelUtil.extractFlowNodeData(getBpmnXml());
    Map<String, String> flowNodeNames = BpmnModelUtil.extractFlowNodeNames(flowNodeData);
    assertNotNull(flowNodeNames, "FlowNode names map should not be null.");
    assertFalse(flowNodeNames.isEmpty(), "FlowNode names map should not be empty.");

    assertTrue(flowNodeNames.containsKey("StartEvent_1"), "Map should contain expected FlowNode ID.");
    assertEquals("Application received", flowNodeNames.get("StartEvent_1"),
        "FlowNode name should match expected value.");
  }

  @Test
  void testGetCollapsedSubprocessElementIds() throws IOException {
    Set<String> collapsedSubprocessIds = BpmnModelUtil.getCollapsedSubprocessElementIds(getBpmnXml());
    assertNotNull(collapsedSubprocessIds, "Collapsed subprocess IDs should not be null.");
    assertTrue(collapsedSubprocessIds.isEmpty(), "No collapsed subprocesses should be present in this BPMN model.");
  }

}
