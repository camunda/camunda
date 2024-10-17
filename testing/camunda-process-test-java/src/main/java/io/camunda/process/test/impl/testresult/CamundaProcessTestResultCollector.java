/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testresult;

import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.client.FlowNodeInstanceDto;
import io.camunda.process.test.impl.client.IncidentDto;
import io.camunda.process.test.impl.client.ProcessInstanceDto;
import io.camunda.process.test.impl.client.VariableDto;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaProcessTestResultCollector {

  private static final Logger LOG =
      LoggerFactory.getLogger(CamundaProcessTestResultCollector.class);

  private final CamundaDataSource dataSource;

  public CamundaProcessTestResultCollector(final CamundaDataSource dataSource) {
    this.dataSource = dataSource;
  }

  public ProcessTestResult collect() {
    final ProcessTestResult result = new ProcessTestResult();

    try {
      final List<ProcessInstanceResult> processInstanceResults =
          dataSource.findProcessInstances().stream()
              .map(this::collectProcessInstanceResult)
              .collect(Collectors.toList());
      result.setProcessInstanceTestResults(processInstanceResults);
    } catch (final IOException e) {
      LOG.warn("Failed to collect the process instance results.", e);
    }

    return result;
  }

  private ProcessInstanceResult collectProcessInstanceResult(
      final ProcessInstanceDto processInstance) {
    final ProcessInstanceResult result = new ProcessInstanceResult();

    final long processInstanceKey = processInstance.getKey();

    result.setProcessInstanceKey(processInstanceKey);
    result.setProcessId(processInstance.getBpmnProcessId());
    result.setVariables(collectVariables(processInstanceKey));
    result.setOpenIncidents(collectOpenIncidents(processInstanceKey));

    return result;
  }

  private Map<String, String> collectVariables(final long processInstanceKey) {
    try {
      return dataSource.getVariablesByProcessInstanceKey(processInstanceKey).stream()
          .collect(Collectors.toMap(VariableDto::getName, VariableDto::getValue));
    } catch (final IOException e) {
      LOG.warn("Failed to collect process instance variables for key '{}'", processInstanceKey, e);
    }
    return Collections.emptyMap();
  }

  private List<OpenIncident> collectOpenIncidents(final long processInstanceKey) {
    try {
      return dataSource.getFlowNodeInstancesByProcessInstanceKey(processInstanceKey).stream()
          .filter(FlowNodeInstanceDto::isIncident)
          .map(this::getIncident)
          .collect(Collectors.toList());
    } catch (final IOException e) {
      LOG.warn(
          "Failed to collect incidents for process instance with key '{}'", processInstanceKey, e);
    }
    return Collections.emptyList();
  }

  private OpenIncident getIncident(final FlowNodeInstanceDto flowNodeInstance) {
    final OpenIncident openIncident = new OpenIncident();
    openIncident.setFlowNodeId(flowNodeInstance.getFlowNodeId());
    openIncident.setFlowNodeName(flowNodeInstance.getFlowNodeName());

    try {
      final IncidentDto incident = dataSource.getIncidentByKey(flowNodeInstance.getIncidentKey());
      openIncident.setType(incident.getType());
      openIncident.setMessage(incident.getMessage());

    } catch (final IOException e) {
      openIncident.setType("?");
      openIncident.setMessage("?");
    }
    return openIncident;
  }
}
