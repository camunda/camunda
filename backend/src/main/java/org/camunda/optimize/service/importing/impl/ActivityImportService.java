package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.es.writer.EventsWriter;
import org.camunda.optimize.service.importing.ImportService;
import org.camunda.optimize.service.importing.diff.MissingActivityFinder;
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
public class ActivityImportService implements ImportService {

  private static final String STATE_COMPLETED = "COMPLETED";
  private static final String STATE_CREATED = "CREATED";
  private final Logger logger = LoggerFactory.getLogger(ActivityImportService.class);

  @Autowired
  private EventsWriter eventsWriter;
  @Autowired
  private MissingActivityFinder missingActivityFinder;

  public void executeImport() {

    List<HistoricActivityInstanceEngineDto> entries = missingActivityFinder.retrieveMissingEntities();

    List<EventDto> events = mapToOptimizeDto(entries);

    try {
      eventsWriter.importEvents(events);
    } catch (Exception e) {
      logger.error("error while writing events to elasticsearch", e);
    }
  }

  private List<EventDto> mapToOptimizeDto(List<HistoricActivityInstanceEngineDto> entries) {
    List<EventDto> result = new ArrayList<>(entries.size());
    for (HistoricActivityInstanceEngineDto entry : entries) {
      final EventDto createEvent = new EventDto();
      createEvent.setState(STATE_CREATED);
      mapDefaults(entry, createEvent);

      result.add(createEvent);

      if (entry.getEndTime() != null) {
        final EventDto completeEvent = new EventDto();
        completeEvent.setState(STATE_COMPLETED);
        mapDefaults(entry, createEvent);
        result.add(completeEvent);
      }
    }

    return result;
  }

  private void mapDefaults(HistoricActivityInstanceEngineDto dto, EventDto createEvent) {
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
