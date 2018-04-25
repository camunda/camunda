package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.dto.engine.HistoricVariableInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableDto;
import org.camunda.optimize.plugin.ImportAdapterProvider;
import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.VariableElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.VariableWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.VariableHelper.isVariableTypeSupported;

public class VariableInstanceImportService {

  private VariableWriter variableWriter;
  private ImportAdapterProvider importAdapterProvider;

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  protected EngineContext engineContext;

  public VariableInstanceImportService(VariableWriter variableWriter,
                                       ImportAdapterProvider importAdapterProvider,
                                       ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                       EngineContext engineContext
  ) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineContext = engineContext;
    this.variableWriter = variableWriter;
    this.importAdapterProvider = importAdapterProvider;
  }

  private List<VariableDto> mapEngineEntitiesToOptimizeEntities(List<HistoricVariableInstanceDto> engineEntities) {
    List<? extends PluginVariableDto> result = mapEngineVariablesToOptimizeVariables(engineEntities);
    List<PluginVariableDto> pluginVariableList = new ArrayList<>(result.size());
    pluginVariableList.addAll(result);
    for (VariableImportAdapter variableImportAdapter : importAdapterProvider.getAdapters()) {
      pluginVariableList = variableImportAdapter.adaptVariables(pluginVariableList);
    }
    return convertPluginListToImportList(pluginVariableList);
  }

  private List<? extends PluginVariableDto> mapEngineVariablesToOptimizeVariables(List<HistoricVariableInstanceDto> engineEntities) {
    return engineEntities
    .stream().map(this::mapEngineEntityToOptimizeEntity)
    .collect(Collectors.toList());
  }

  private VariableDto mapEngineEntityToOptimizeEntity(HistoricVariableInstanceDto engineEntity) {
    VariableDto optimizeDto = new VariableDto();
    optimizeDto.setId(engineEntity.getId());
    optimizeDto.setName(engineEntity.getName());
    optimizeDto.setType(engineEntity.getType());
    optimizeDto.setValue(engineEntity.getValue());

    optimizeDto.setProcessDefinitionId(engineEntity.getProcessDefinitionId());
    optimizeDto.setProcessDefinitionKey(engineEntity.getProcessDefinitionKey());
    optimizeDto.setProcessInstanceId(engineEntity.getProcessInstanceId());

    return optimizeDto;
  }

  private List<VariableDto> convertPluginListToImportList(List<PluginVariableDto> pluginVariableList) {
    List<VariableDto> variableImportList = new ArrayList<>(pluginVariableList.size());
    for (PluginVariableDto dto : pluginVariableList) {
      if (isValidVariable(dto)) {
        if( dto instanceof VariableDto) {
          variableImportList.add((VariableDto) dto);
        } else {
          variableImportList.add(convertPluginVariableToImportVariable(dto));
        }
      }
    }
    return variableImportList;
  }

  private VariableDto convertPluginVariableToImportVariable(PluginVariableDto pluginVariableDto) {
    VariableDto variableDto = new VariableDto();
    variableDto.setId(pluginVariableDto.getId());
    variableDto.setName(pluginVariableDto.getName());
    variableDto.setValue(pluginVariableDto.getValue());
    variableDto.setType(pluginVariableDto.getType());
    variableDto.setProcessInstanceId(pluginVariableDto.getProcessInstanceId());
    variableDto.setProcessDefinitionId(pluginVariableDto.getProcessDefinitionId());
    variableDto.setProcessDefinitionKey(pluginVariableDto.getProcessDefinitionKey());
    return variableDto;
  }

  private boolean isValidVariable(PluginVariableDto variableDto) {
    if (variableDto == null) {
      logger.debug("Refuse to add null variable from import adapter plugin.");
      return false;
    } else if (isNullOrEmpty(variableDto.getName())) {
      logger.debug("Refuse to add variable with id [{}] from variable import adapter plugin. Variable has no name.",
        variableDto.getId());
      return false;
    } else if (isNullOrEmpty(variableDto.getType()) || !isVariableTypeSupported(variableDto.getType())) {
      logger.debug("Refuse to add variable [{}] from variable import adapter plugin. Variable has no type or type is not supported.",
        variableDto.getName());
      return false;
    } else if (isNullOrEmpty(variableDto.getProcessInstanceId())) {
      logger.debug("Refuse to add variable [{}] from variable import adapter plugin. Variable has no process instance id.",
        variableDto.getName());
      return false;
    } else if (isNullOrEmpty(variableDto.getProcessDefinitionId())) {
      logger.debug("Refuse to add variable [{}] from variable import adapter plugin. Variable has no process definition id.",
        variableDto.getName());
      return false;
    } else if (isNullOrEmpty(variableDto.getProcessDefinitionKey())) {
      logger.debug("Refuse to add variable [{}] from variable import adapter plugin. Variable has no process definition key.",
        variableDto.getName());
      return false;
    }
    return true;
  }

  private boolean isNullOrEmpty(String value) {
    return value == null || value.isEmpty();
  }

  public void executeImport(List<HistoricVariableInstanceDto> variablesFromEngine,
                            Set<String> processInstanceIds) {
    logger.trace("Importing entities from engine...");

    boolean newDataIsAvailable = !variablesFromEngine.isEmpty();
    VariableElasticsearchImportJob variableImportJob = new VariableElasticsearchImportJob(variableWriter);
    if (newDataIsAvailable) {
      List<VariableDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(variablesFromEngine);
      variableImportJob.setEntitiesToImport(newOptimizeEntities);
    }
    variableImportJob.setProcessInstanceIdsVariablesHaveBeenImportedFor(new ArrayList<>(processInstanceIds));
    addElasticsearchImportJobToQueue(variableImportJob);
  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob elasticsearchImportJob) {
    try {
      elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
    } catch (InterruptedException e) {
      logger.error("Was interrupted while trying to add new job to Elasticsearch import queue.", e);
    }
  }
}
