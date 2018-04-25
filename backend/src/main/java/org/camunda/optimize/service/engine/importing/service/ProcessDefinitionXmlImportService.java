package org.camunda.optimize.service.engine.importing.service;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.ProcessDefinitionXmlElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ProcessDefinitionXmlWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProcessDefinitionXmlImportService {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private MissingEntitiesFinder<ProcessDefinitionXmlEngineDto> missingXmlFinder;
  protected EngineContext engineContext;
  private ProcessDefinitionXmlWriter processDefinitionXmlWriter;

  public ProcessDefinitionXmlImportService(
      ProcessDefinitionXmlWriter processDefinitionXmlWriter,
      ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
      MissingEntitiesFinder<ProcessDefinitionXmlEngineDto> missingXmlFinder,
      EngineContext engineContext
  ) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.missingXmlFinder = missingXmlFinder;
    this.engineContext = engineContext;
    this.processDefinitionXmlWriter = processDefinitionXmlWriter;

  }

  public void executeImport(List<ProcessDefinitionXmlEngineDto> pageOfEngineEntities) {
    logger.trace("Importing entities from engine...");

    List<ProcessDefinitionXmlEngineDto> newEngineEntities =
          missingXmlFinder.retrieveMissingEntities(pageOfEngineEntities);
    boolean newDataIsAvailable = !newEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      List<ProcessDefinitionXmlOptimizeDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(newEngineEntities);
      ElasticsearchImportJob<ProcessDefinitionXmlOptimizeDto> elasticsearchImportJob =
        createElasticsearchImportJob(newOptimizeEntities);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob elasticsearchImportJob) {
    try {
      elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
    } catch (InterruptedException e) {
      logger.error("Was interrupted while trying to add new job to Elasticsearch import queue.", e);
    }
  }

  private List<ProcessDefinitionXmlOptimizeDto> mapEngineEntitiesToOptimizeEntities(List<ProcessDefinitionXmlEngineDto> engineEntities) {
    return engineEntities
      .stream().map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<ProcessDefinitionXmlOptimizeDto>
  createElasticsearchImportJob(List<ProcessDefinitionXmlOptimizeDto> processDefinitions) {
    ProcessDefinitionXmlElasticsearchImportJob procDefImportJob = new ProcessDefinitionXmlElasticsearchImportJob(processDefinitionXmlWriter);
    procDefImportJob.setEntitiesToImport(processDefinitions);
    return procDefImportJob;
  }

  private ProcessDefinitionXmlOptimizeDto mapEngineEntityToOptimizeEntity(ProcessDefinitionXmlEngineDto engineEntity) {
    ProcessDefinitionXmlOptimizeDto optimizeDto = new ProcessDefinitionXmlOptimizeDto();
    optimizeDto.setBpmn20Xml(engineEntity.getBpmn20Xml());
    optimizeDto.setProcessDefinitionId(engineEntity.getId());
    optimizeDto.setFlowNodeNames(constructFlowNodeNames(engineEntity.getBpmn20Xml()));
    optimizeDto.setEngine(engineContext.getEngineAlias());
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
