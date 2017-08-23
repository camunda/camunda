package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.importing.diff.MissingProcessDefinitionXmlFinder;
import org.camunda.optimize.service.importing.fetcher.AllEntitiesBasedProcessDefinitionXmlFetcher;
import org.camunda.optimize.service.importing.index.AllEntitiesBasedImportIndexHandler;
import org.camunda.optimize.service.importing.job.importing.ProcessDefinitionXmlImportJob;
import org.camunda.optimize.service.importing.job.schedule.PageBasedImportScheduleJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProcessDefinitionXmlImportService extends
    PaginatedImportService<ProcessDefinitionXmlEngineDto, ProcessDefinitionXmlOptimizeDto, AllEntitiesBasedImportIndexHandler> {
  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionXmlImportService.class);

  @Autowired
  private ProcessDefinitionWriter procDefWriter;

  @Autowired
  private MissingProcessDefinitionXmlFinder xmlFinder;

  @Autowired
  private AllEntitiesBasedProcessDefinitionXmlFetcher processDefinitionXmlFetcher;

  @Override
  protected MissingEntitiesFinder<ProcessDefinitionXmlEngineDto> getMissingEntitiesFinder() {
    return xmlFinder;
  }

  @Override
  public Class<AllEntitiesBasedImportIndexHandler> getIndexHandlerType() {
    return AllEntitiesBasedImportIndexHandler.class;
  }

  @Override
  protected List<ProcessDefinitionXmlEngineDto> queryEngineRestPoint(PageBasedImportScheduleJob job) throws OptimizeException {
    return processDefinitionXmlFetcher.fetchProcessDefinitionXmls(
      job.getAbsoluteImportIndex(),
      job.getEngineAlias()
    );
  }

  @Override
  public int getEngineEntityCount(AllEntitiesBasedImportIndexHandler indexHandler, String engineAlias) throws OptimizeException {
    return processDefinitionXmlFetcher
      .fetchProcessDefinitionCount(engineAlias);
  }

  @Override
  public void importToElasticSearch(List<ProcessDefinitionXmlOptimizeDto> newOptimizeEntriesToImport) {
    ProcessDefinitionXmlImportJob procDefXmlImportJob = new ProcessDefinitionXmlImportJob(procDefWriter);
    procDefXmlImportJob.setEntitiesToImport(newOptimizeEntriesToImport);
    try {
      importJobExecutor.executeImportJob(procDefXmlImportJob);
    } catch (InterruptedException e) {
      logger.error("Interruption during import of process definition xml import job!", e);
    }
  }

  @Override
  public ProcessDefinitionXmlOptimizeDto mapToOptimizeDto(ProcessDefinitionXmlEngineDto entry, String engineAlias) {
    ProcessDefinitionXmlOptimizeDto processDefinitionXmlOptimizeDto = mapDefaults(entry);
    processDefinitionXmlOptimizeDto.setEngine(engineAlias);
    return processDefinitionXmlOptimizeDto;
  }

  private ProcessDefinitionXmlOptimizeDto mapDefaults(ProcessDefinitionXmlEngineDto dto) {
    ProcessDefinitionXmlOptimizeDto optimizeDto = new ProcessDefinitionXmlOptimizeDto();
    optimizeDto.setBpmn20Xml(dto.getBpmn20Xml());
    optimizeDto.setId(dto.getId());
    return optimizeDto;
  }

  @Override
  public String getElasticsearchType() {
    return configurationService.getProcessDefinitionXmlType();
  }
}
