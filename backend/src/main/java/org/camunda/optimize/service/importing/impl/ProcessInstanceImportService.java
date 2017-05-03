package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.es.writer.ProcessInstanceWriter;
import org.camunda.optimize.service.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.importing.diff.MissingProcessInstanceFinder;
import org.camunda.optimize.service.importing.job.impl.ProcessInstanceImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProcessInstanceImportService extends PaginatedImportService<HistoricProcessInstanceDto, ProcessInstanceDto> {

  private final Logger logger = LoggerFactory.getLogger(ProcessInstanceImportService.class);

  @Autowired
  private ProcessInstanceWriter processInstanceWriter;
  @Autowired
  private MissingProcessInstanceFinder missingProcessInstanceFinder;

  @Override
  protected MissingEntitiesFinder<HistoricProcessInstanceDto> getMissingEntitiesFinder() {
    return missingProcessInstanceFinder;
  }

  @Override
  protected List<HistoricProcessInstanceDto> queryEngineRestPoint(int indexOfFirstResult, int maxPageSize) {
    return engineEntityFetcher.fetchHistoricProcessInstances(indexOfFirstResult, maxPageSize);
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
  public List<ProcessInstanceDto> mapToOptimizeDto(List<HistoricProcessInstanceDto> entries) {
    List<ProcessInstanceDto> result = new ArrayList<>(entries.size());
    for (HistoricProcessInstanceDto entry : entries) {
      final ProcessInstanceDto procInst = new ProcessInstanceDto();
      mapDefaults(entry, procInst);
      result.add(procInst);

    }
    return result;
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
