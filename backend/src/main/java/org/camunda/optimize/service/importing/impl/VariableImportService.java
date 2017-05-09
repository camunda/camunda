package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.HistoricVariableInstanceDto;
import org.camunda.optimize.dto.optimize.VariableDto;
import org.camunda.optimize.service.es.writer.VariableWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.importing.diff.MissingVariablesFinder;
import org.camunda.optimize.service.importing.job.impl.VariableImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class VariableImportService extends IndexedImportService<HistoricVariableInstanceDto, VariableDto> {

  private final Logger logger = LoggerFactory.getLogger(VariableImportService.class);

  @Autowired
  private VariableWriter variableWriter;
  @Autowired
  private MissingVariablesFinder missingVariablesFinder;

  @Override
  protected List<HistoricVariableInstanceDto> queryEngineRestPoint(Set<String> processInstanceIds) throws OptimizeException {
    return engineEntityFetcher.fetchHistoricVariableInstances(processInstanceIds);
  }

  @Override
  protected MissingEntitiesFinder<HistoricVariableInstanceDto> getMissingEntitiesFinder() {
    return missingVariablesFinder;
  }

  @Override
  protected List<VariableDto> mapToOptimizeDto(List<HistoricVariableInstanceDto> entries) {
    List<VariableDto> result = new ArrayList<>(entries.size());
    for (HistoricVariableInstanceDto entry : entries) {
      mapDefaults(entry);
      result.add(mapDefaults(entry));
    }
    return result;
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
