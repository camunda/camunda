/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.webapp.rest.dto.incidents.IncidentDto.FALLBACK_PROCESS_DEFINITION_NAME;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.FlowNodeStore;
import io.camunda.operate.store.IncidentStore;
import io.camunda.operate.webapp.data.IncidentDataHolder;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentErrorTypeDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentFlowNodeDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentResponseDto;
import io.camunda.webapps.operate.TreePath;
import io.camunda.webapps.schema.entities.incident.ErrorType;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchIncidentReader extends OpensearchAbstractReader implements IncidentReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchIncidentReader.class);

  @Autowired private OperationReader operationReader;

  @Autowired private ProcessInstanceReader processInstanceReader;

  @Autowired private ProcessCache processCache;

  @Autowired private IncidentStore incidentStore;

  @Autowired private FlowNodeStore flowNodeStore;

  @Override
  public List<IncidentEntity> getAllIncidentsByProcessInstanceKey(final Long processInstanceKey) {
    return incidentStore.getIncidentsByProcessInstanceKey(processInstanceKey);
  }

  /**
   * Returns map of incident ids per process instance id.
   *
   * @param processInstanceKeys
   * @return
   */
  @Override
  public Map<Long, List<Long>> getIncidentKeysPerProcessInstance(
      final List<Long> processInstanceKeys) {
    return incidentStore.getIncidentKeysPerProcessInstance(processInstanceKeys);
  }

  @Override
  public IncidentEntity getIncidentById(final Long incidentKey) {
    return incidentStore.getIncidentById(incidentKey);
  }

  @Override
  public IncidentResponseDto getIncidentsByProcessInstanceId(final String processInstanceId) {
    // get treePath for process instance
    final String treePath = processInstanceReader.getProcessInstanceTreePath(processInstanceId);

    final List<Map<ErrorType, Long>> errorTypes = new ArrayList<>();
    final List<IncidentEntity> incidents =
        incidentStore.getIncidentsWithErrorTypesFor(treePath, errorTypes);

    final IncidentResponseDto incidentResponse = new IncidentResponseDto();
    incidentResponse.setErrorTypes(
        errorTypes.stream()
            .map(
                m -> {
                  final var entry = m.entrySet().iterator().next();
                  return IncidentErrorTypeDto.createFrom(entry.getKey())
                      .setCount(entry.getValue().intValue());
                })
            .collect(Collectors.toList()));

    final Map<Long, String> processNames = new HashMap<>();
    incidents.stream()
        .filter(inc -> processNames.get(inc.getProcessDefinitionKey()) == null)
        .forEach(
            inc ->
                processNames.put(
                    inc.getProcessDefinitionKey(),
                    processCache.getProcessNameOrBpmnProcessId(
                        inc.getProcessDefinitionKey(), FALLBACK_PROCESS_DEFINITION_NAME)));

    final Map<Long, List<OperationEntity>> operations =
        operationReader.getOperationsPerIncidentKey(processInstanceId);

    final Map<String, IncidentDataHolder> incData =
        collectFlowNodeDataForPropagatedIncidents(incidents, processInstanceId, treePath);

    // collect flow node statistics
    incidentResponse.setFlowNodes(
        incData.values().stream()
            .collect(
                Collectors.groupingBy(
                    IncidentDataHolder::getFinalFlowNodeId, Collectors.counting()))
            .entrySet()
            .stream()
            .map(entry -> new IncidentFlowNodeDto(entry.getKey(), entry.getValue().intValue()))
            .collect(Collectors.toList()));

    final List<IncidentDto> incidentsDtos =
        IncidentDto.sortDefault(
            IncidentDto.createFrom(incidents, operations, processNames, incData));
    incidentResponse.setIncidents(incidentsDtos);
    incidentResponse.setCount(incidents.size());
    return incidentResponse;
  }

  /**
   * Returns map incidentId -> IncidentDataHolder.
   *
   * @param incidents
   * @param processInstanceId
   * @param currentTreePath
   * @return
   */
  @Override
  public Map<String, IncidentDataHolder> collectFlowNodeDataForPropagatedIncidents(
      final List<IncidentEntity> incidents,
      final String processInstanceId,
      final String currentTreePath) {

    final Set<String> flowNodeInstanceIdsSet = new HashSet<>();
    final Map<String, IncidentDataHolder> incDatas = new HashMap<>();
    for (final IncidentEntity inc : incidents) {
      final IncidentDataHolder incData = new IncidentDataHolder().setIncidentId(inc.getId());
      if (!String.valueOf(inc.getProcessInstanceKey()).equals(processInstanceId)) {
        final String callActivityInstanceId =
            TreePath.extractFlowNodeInstanceId(inc.getTreePath(), currentTreePath);
        incData.setFinalFlowNodeInstanceId(callActivityInstanceId);
        flowNodeInstanceIdsSet.add(callActivityInstanceId);
      } else {
        incData.setFinalFlowNodeInstanceId(String.valueOf(inc.getFlowNodeInstanceKey()));
        incData.setFinalFlowNodeId(inc.getFlowNodeId());
      }
      incDatas.put(inc.getId(), incData);
    }

    if (flowNodeInstanceIdsSet.size() > 0) {
      // select flowNodeIds by flowNodeInstanceIds
      final Map<String, String> flowNodeIdsMap =
          flowNodeStore.getFlowNodeIdsForFlowNodeInstances(flowNodeInstanceIdsSet);

      // set flow node id, where not yet set
      incDatas.values().stream()
          .filter(iData -> iData.getFinalFlowNodeId() == null)
          .forEach(
              iData ->
                  iData.setFinalFlowNodeId(flowNodeIdsMap.get(iData.getFinalFlowNodeInstanceId())));
    }
    return incDatas;
  }
}
