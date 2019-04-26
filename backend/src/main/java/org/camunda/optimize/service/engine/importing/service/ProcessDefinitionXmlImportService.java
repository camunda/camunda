/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.ProcessDefinitionXmlElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ProcessDefinitionXmlWriter;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
public class ProcessDefinitionXmlImportService implements ImportService<ProcessDefinitionXmlEngineDto> {
  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EngineContext engineContext;
  private final ProcessDefinitionXmlWriter processDefinitionXmlWriter;

  @Override
  public void executeImport(List<ProcessDefinitionXmlEngineDto> pageOfEngineEntities) {
    log.trace("Importing entities from engine...");

    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      List<ProcessDefinitionOptimizeDto> newOptimizeEntities =
        mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      ElasticsearchImportJob<ProcessDefinitionOptimizeDto> elasticsearchImportJob =
        createElasticsearchImportJob(newOptimizeEntities);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  @Override
  public void executeImport(final List<ProcessDefinitionXmlEngineDto> pageOfEngineEntities, final Runnable callback) {
    executeImport(pageOfEngineEntities);
  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<ProcessDefinitionOptimizeDto> mapEngineEntitiesToOptimizeEntities(List<ProcessDefinitionXmlEngineDto>
                                                                                   engineEntities) {
    return engineEntities
      .stream().map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<ProcessDefinitionOptimizeDto> createElasticsearchImportJob(
    List<ProcessDefinitionOptimizeDto> processDefinitions) {
    ProcessDefinitionXmlElasticsearchImportJob procDefImportJob = new ProcessDefinitionXmlElasticsearchImportJob(
      processDefinitionXmlWriter
    );
    procDefImportJob.setEntitiesToImport(processDefinitions);
    return procDefImportJob;
  }

  private ProcessDefinitionOptimizeDto mapEngineEntityToOptimizeEntity(ProcessDefinitionXmlEngineDto engineEntity) {
    final ProcessDefinitionOptimizeDto optimizeDto = new ProcessDefinitionOptimizeDto(
      engineEntity.getId(),
      engineContext.getEngineAlias(),
      engineEntity.getBpmn20Xml(),
      constructFlowNodeNames(engineEntity.getBpmn20Xml())
    );
    return optimizeDto;
  }

  private Map<String, String> constructFlowNodeNames(String bpmn20Xml) {
    Map<String, String> result = new HashMap<>();
    BpmnModelInstance model = Bpmn.readModelFromStream(new ByteArrayInputStream(bpmn20Xml.getBytes()));
    for (FlowNode node : model.getModelElementsByType(FlowNode.class)) {
      result.put(node.getId(), node.getName());
    }
    return result;
  }

}
