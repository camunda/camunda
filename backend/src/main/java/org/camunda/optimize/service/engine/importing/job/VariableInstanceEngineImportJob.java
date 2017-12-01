package org.camunda.optimize.service.engine.importing.job;

import org.camunda.optimize.dto.engine.HistoricVariableInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableDto;
import org.camunda.optimize.plugin.ImportAdapterProvider;
import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.EngineEntityFetcher;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.VariableElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.VariableWriter;

import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.service.util.VariableHelper.isVariableTypeSupported;

public class VariableInstanceEngineImportJob extends
  EngineImportJob<HistoricVariableInstanceDto, VariableDto, IdSetBasedImportPage> {

  private VariableWriter variableWriter;
  private ImportAdapterProvider importAdapterProvider;

  public VariableInstanceEngineImportJob(VariableWriter variableWriter,
                                         ImportAdapterProvider importAdapterProvider,
                                         IdSetBasedImportPage importIndex,
                                         ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                         MissingEntitiesFinder<HistoricVariableInstanceDto> missingEntitiesFinder,
                                         EngineEntityFetcher<HistoricVariableInstanceDto,
                                         IdSetBasedImportPage> engineEntityFetcher,
                                         EngineContext engineContext
  ) {
    super(importIndex, elasticsearchImportJobExecutor, missingEntitiesFinder, engineEntityFetcher, engineContext);
    this.variableWriter = variableWriter;
    this.importAdapterProvider = importAdapterProvider;
  }

  @Override
  protected List<VariableDto> mapEngineEntitiesToOptimizeEntities(List<HistoricVariableInstanceDto> engineEntities) {
    List<? extends PluginVariableDto> result =  super.mapEngineEntitiesToOptimizeEntities(engineEntities);
    List<PluginVariableDto> pluginVariableList = new ArrayList<>(result.size());
    pluginVariableList.addAll(result);
    for (VariableImportAdapter variableImportAdapter : importAdapterProvider.getAdapters()) {
      pluginVariableList = variableImportAdapter.adaptVariables(pluginVariableList);
    }
    return convertPluginListToImportList(pluginVariableList);
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

  @Override
  protected ElasticsearchImportJob<VariableDto>
        createElasticsearchImportJob(List<VariableDto> variables) {
    VariableElasticsearchImportJob variableImportJob = new VariableElasticsearchImportJob(variableWriter);
    variableImportJob.setEntitiesToImport(variables);
    return variableImportJob;
  }

  @Override
  protected VariableDto mapEngineEntityToOptimizeEntity(HistoricVariableInstanceDto engineEntity) {
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

}
