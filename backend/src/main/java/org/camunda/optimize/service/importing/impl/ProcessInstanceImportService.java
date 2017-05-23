package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.es.writer.ProcessInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.importing.diff.MissingProcessInstanceFinder;
import org.camunda.optimize.service.importing.job.importing.ProcessInstanceImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class ProcessInstanceImportService extends IdBasedImportService<HistoricProcessInstanceDto, ProcessInstanceDto> {

  private final Logger logger = LoggerFactory.getLogger(ProcessInstanceImportService.class);

  @Autowired
  private ProcessInstanceWriter processInstanceWriter;
  @Autowired
  private MissingProcessInstanceFinder missingProcessInstanceFinder;

  @Override
  protected MissingEntitiesFinder<HistoricProcessInstanceDto> getMissingEntitiesFinder() {
    return missingProcessInstanceFinder;
  }

  protected List<HistoricProcessInstanceDto> queryEngineRestPoint(Set<String> idsToFetch) throws OptimizeException {
    return engineEntityFetcher.fetchHistoricProcessInstances(idsToFetch);
  }

  @Override
  public void importToElasticSearch(List<ProcessInstanceDto> processInstances) {
    ProcessInstanceImportJob processInstanceImportJob = new ProcessInstanceImportJob(processInstanceWriter);
    processInstanceImportJob.setEntitiesToImport(processInstances);
    try {
      importJobExecutor.executeImportJob(processInstanceImportJob);
    } catch (InterruptedException e) {
      logger.error("Interruption during import of activity job!", e);
    }
  }

  @Override
  public ProcessInstanceDto mapToOptimizeDto(HistoricProcessInstanceDto entry) {
    final ProcessInstanceDto procInst = new ProcessInstanceDto();
    mapDefaults(entry, procInst);
    return procInst;
  }

  private void mapDefaults(HistoricProcessInstanceDto dto, ProcessInstanceDto procInst) {
    procInst.setProcessDefinitionKey(dto.getProcessDefinitionKey());
    procInst.setProcessDefinitionId(dto.getProcessDefinitionId());
    procInst.setProcessInstanceId(dto.getId());
    procInst.setStartDate(dto.getStartTime());
    procInst.setEndDate(dto.getEndTime());
  }

  @Override
  public String getElasticsearchType() {
    return configurationService.getProcessInstanceType();
  }
}
