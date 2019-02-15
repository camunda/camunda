package org.camunda.optimize.service.engine.importing.service;

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
  protected EngineContext engineContext;
  private ProcessDefinitionXmlWriter processDefinitionXmlWriter;

  public ProcessDefinitionXmlImportService(
    ProcessDefinitionXmlWriter processDefinitionXmlWriter,
    ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
    EngineContext engineContext
  ) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineContext = engineContext;
    this.processDefinitionXmlWriter = processDefinitionXmlWriter;

  }

  public void executeImport(List<ProcessDefinitionXmlEngineDto> pageOfEngineEntities) {
    logger.trace("Importing entities from engine...");

    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      List<ProcessDefinitionOptimizeDto> newOptimizeEntities =
        mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      ElasticsearchImportJob<ProcessDefinitionOptimizeDto> elasticsearchImportJob =
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
    ProcessDefinitionOptimizeDto optimizeDto = new ProcessDefinitionOptimizeDto();
    optimizeDto.setBpmn20Xml(engineEntity.getBpmn20Xml());
    optimizeDto.setId(engineEntity.getId());
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
