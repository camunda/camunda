package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.es.writer.EventsWriter;
import org.camunda.optimize.service.importing.diff.MissingActivityFinder;
import org.camunda.optimize.service.importing.diff.MissingEntitiesFinder;
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

  private static final String STATE_COMPLETED = "COMPLETED";
  private static final String STATE_CREATED = "CREATED";
  private final Logger logger = LoggerFactory.getLogger(ActivityImportService.class);

  @Autowired
  private EventsWriter eventsWriter;
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
    try {
      eventsWriter.importEvents(events);
    } catch (Exception e) {
      logger.error("error while writing events to elasticsearch", e);
    }
  }

  @Override
  public List<EventDto> mapToOptimizeDto(List<HistoricActivityInstanceEngineDto> entries) {
    List<EventDto> result = new ArrayList<>(entries.size());
    for (HistoricActivityInstanceEngineDto entry : entries) {
      final EventDto createEvent = new EventDto();
      createEvent.setState(STATE_CREATED);
      mapDefaults(entry, createEvent);

      result.add(createEvent);

      if (entry.getEndTime() != null) {
        final EventDto completeEvent = new EventDto();
        completeEvent.setState(STATE_COMPLETED);
        mapDefaults(entry, completeEvent);
        setActivityInstanceIdForCompleteEvent(completeEvent);
        result.add(completeEvent);
      }
    }

    return result;
  }

  private void setActivityInstanceIdForCompleteEvent(EventDto completeEvent) {
    String newId = completeEvent.getId() +  "_" + STATE_COMPLETED;
    completeEvent.setId(newId);
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
  }

}
