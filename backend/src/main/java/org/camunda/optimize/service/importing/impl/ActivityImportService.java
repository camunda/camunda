package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.es.writer.BranchAnalysisDataWriter;
import org.camunda.optimize.service.es.writer.EventsWriter;
import org.camunda.optimize.service.importing.diff.MissingActivityFinder;
import org.camunda.optimize.service.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.importing.job.impl.BranchAnalysisImportJob;
import org.camunda.optimize.service.importing.job.impl.EventImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
@Component
public class ActivityImportService extends PaginatedImportService<HistoricActivityInstanceEngineDto, EventDto> {

  private final Logger logger = LoggerFactory.getLogger(ActivityImportService.class);

  @Autowired
  private EventsWriter eventsWriter;
  @Autowired
  private BranchAnalysisDataWriter branchAnalysisDataWriter;
  @Autowired
  private MissingActivityFinder missingActivityFinder;

  @Override
  protected MissingEntitiesFinder<HistoricActivityInstanceEngineDto> getMissingEntitiesFinder() {
    return missingActivityFinder;
  }

  @Override
  protected List<HistoricActivityInstanceEngineDto> queryEngineRestPoint(int indexOfFirstResult, int maxPageSize) {
    return engineEntityFetcher.fetchHistoricActivityInstances(indexOfFirstResult, maxPageSize);
  }

  @Override
  public void importToElasticSearch(List<EventDto> events) {
    EventImportJob eventImportJob = new EventImportJob(engineEntityFetcher, eventsWriter);
    eventImportJob.setEntitiesToImport(events);
    BranchAnalysisImportJob branchAnalysisImportJob = new BranchAnalysisImportJob(branchAnalysisDataWriter);
    branchAnalysisImportJob.setEntitiesToImport(events);
    try {
      importJobExecutor.executeImportJob(eventImportJob);
      importJobExecutor.executeImportJob(branchAnalysisImportJob);
    } catch (InterruptedException e) {
      logger.error("Interruption during import of activity job!", e);
    }
  }

  @Override
  public List<EventDto> mapToOptimizeDto(List<HistoricActivityInstanceEngineDto> entries) {
    List<EventDto> result = new ArrayList<>(entries.size());
    for (HistoricActivityInstanceEngineDto entry : entries) {
      final EventDto createEvent = new EventDto();
      mapDefaults(entry, createEvent);
      result.add(createEvent);

    }
    return result;
  }

  private void mapDefaults(HistoricActivityInstanceEngineDto dto, EventDto createEvent) {
    createEvent.setId(dto.getId());
    createEvent.setActivityId(dto.getActivityId());
    createEvent.setActivityInstanceId(dto.getParentActivityInstanceId());
    createEvent.setTimestamp(dto.getStartTime());
    createEvent.setProcessDefinitionKey(dto.getProcessDefinitionKey());
    createEvent.setProcessDefinitionId(dto.getProcessDefinitionId());
    createEvent.setProcessInstanceId(dto.getProcessInstanceId());
    createEvent.setStartDate(dto.getStartTime());
    createEvent.setEndDate(dto.getEndTime());
    createEvent.setActivityType(dto.getActivityType());
    createEvent.setDurationInMs(dto.getDurationInMillis());
  }

}
