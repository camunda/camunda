package org.camunda.optimize.service.importing.strategy;

import org.camunda.optimize.dto.engine.HistoricVariableInstanceDto;
import org.camunda.optimize.dto.optimize.variable.VariableDto;
import org.camunda.optimize.plugin.ImportAdapterProvider;
import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import org.camunda.optimize.service.es.writer.VariableWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.importing.diff.MissingVariablesFinder;
import org.camunda.optimize.service.importing.impl.IdBasedImportService;
import org.camunda.optimize.service.importing.job.importing.VariableImportJob;
import org.camunda.optimize.service.importing.provider.ImportServiceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.camunda.optimize.service.util.VariableHelper.isVariableTypeSupported;

@Component
public class VariableImportService extends IdBasedImportService<HistoricVariableInstanceDto, VariableDto> {

  private final Logger logger = LoggerFactory.getLogger(VariableImportService.class);

  @Autowired
  private VariableWriter variableWriter;
  @Autowired
  private MissingVariablesFinder missingVariablesFinder;

  @Autowired
  private ImportAdapterProvider importServiceProvider;

  @Override
  protected List<HistoricVariableInstanceDto> queryEngineRestPoint(Set<String> processInstanceIds) throws OptimizeException {
    return engineEntityFetcher.fetchHistoricVariableInstances(processInstanceIds);
  }

  @Override
  protected MissingEntitiesFinder<HistoricVariableInstanceDto> getMissingEntitiesFinder() {
    return missingVariablesFinder;
  }

  @Override
  protected List<VariableDto> processNewEngineEntries(List<HistoricVariableInstanceDto> entries) {
    List<? extends PluginVariableDto> result = super.processNewEngineEntries(entries);
    List<PluginVariableDto> pluginVariableList = new ArrayList<>(result.size());
    pluginVariableList.addAll(result);
    for (VariableImportAdapter variableImportAdapter : importServiceProvider.getAdapters()) {
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
  protected VariableDto mapToOptimizeDto(HistoricVariableInstanceDto entry) {
    return mapDefaults(entry);
  }

  private VariableDto mapDefaults(HistoricVariableInstanceDto dto) {
    VariableDto optimizeDto = new VariableDto();
    optimizeDto.setId(dto.getId());
    optimizeDto.setName(dto.getName());
    optimizeDto.setType(dto.getType());
    optimizeDto.setValue(dto.getValue());

    optimizeDto.setProcessDefinitionId(dto.getProcessDefinitionId());
    optimizeDto.setProcessDefinitionKey(dto.getProcessDefinitionKey());
    optimizeDto.setProcessInstanceId(dto.getProcessInstanceId());

    return optimizeDto;
  }

  @Override
  protected void importToElasticSearch(List<VariableDto> variables) {
    VariableImportJob variableImportJob = new VariableImportJob(variableWriter);
    variableImportJob.setEntitiesToImport(variables);
    try {
      importJobExecutor.executeImportJob(variableImportJob);
    } catch (InterruptedException e) {
      logger.error("Interruption during import of variable import job!", e);
    }
  }

  @Override
  public String getElasticsearchType() {
    return configurationService.getVariableType();
  }
}
