package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.importing.diff.MissingProcessDefinitionXmlFinder;
import org.camunda.optimize.service.importing.fetcher.IdBasedProcessDefinitionXmlFetcher;
import org.camunda.optimize.service.importing.index.DefinitionBasedImportIndexHandler;
import org.camunda.optimize.service.importing.job.importing.ProcessDefinitionXmlImportJob;
import org.camunda.optimize.service.importing.job.schedule.PageBasedImportScheduleJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProcessDefinitionXmlIdBasedImportService
    extends PaginatedImportService<ProcessDefinitionXmlEngineDto, ProcessDefinitionXmlOptimizeDto, DefinitionBasedImportIndexHandler> {
  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionXmlIdBasedImportService.class);

  @Autowired
  private ProcessDefinitionWriter procDefWriter;

  @Autowired
  private MissingProcessDefinitionXmlFinder xmlFinder;

  @Autowired
  private IdBasedProcessDefinitionXmlFetcher idBasedProcessDefinitionXmlFetcher;


  @Override
  protected MissingEntitiesFinder<ProcessDefinitionXmlEngineDto> getMissingEntitiesFinder() {
    return xmlFinder;
  }

  @Override
  public Class<DefinitionBasedImportIndexHandler> getIndexHandlerType() {
    return DefinitionBasedImportIndexHandler.class;
  }

  @Override
  protected List<ProcessDefinitionXmlEngineDto> queryEngineRestPoint(PageBasedImportScheduleJob job) throws OptimizeException {
    return idBasedProcessDefinitionXmlFetcher.fetchProcessDefinitionXmls(
      job.getCurrentDefinitionBasedImportIndex(),
      job.getCurrentProcessDefinitionId(),
      job.getEngineAlias()
    );
  }

  @Override
  public int getEngineEntityCount(DefinitionBasedImportIndexHandler definitionBasedImportIndexHandler, String engineAlias) throws OptimizeException {
    return idBasedProcessDefinitionXmlFetcher
      .fetchProcessDefinitionCount(definitionBasedImportIndexHandler.getAllProcessDefinitions(), engineAlias);
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

  @Override
  public boolean isProcessDefinitionBased() {
    return true;
  }
}
