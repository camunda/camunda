package org.camunda.optimize.mapper;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceDto;
import org.camunda.optimize.dto.optimize.EventDto;

import java.util.Collection;

/**
 * @author Askar Akhmerov
 */
public class EventMappingHelper {

  private static final String STATE_COMPLETED = "COMPLETED";
  private static final String STATE_CREATED = "CREATED";

  public static void map(HistoricActivityInstanceDto dto, Collection<EventDto> result) {
    final EventDto createEvent = new EventDto();
    createEvent.setState(STATE_CREATED);
    mapDefaults(dto, createEvent);

    result.add(createEvent);

    if (dto.getEndTime() != null) {
      final EventDto completeEvent = new EventDto();
      completeEvent.setState(STATE_COMPLETED);
      mapDefaults(dto, createEvent);

      result.add(completeEvent);
    }
  }

  private static void mapDefaults(HistoricActivityInstanceDto dto, EventDto createEvent) {
    createEvent.setActivityId(dto.getActivityId());
    createEvent.setActivityInstanceId(dto.getParentActivityInstanceId());
    createEvent.setTimestamp(dto.getStartTime());
    createEvent.setProcessDefinitionKey(dto.getProcessDefinitionKey());
    createEvent.setProcessInstanceId(dto.getProcessInstanceId());
  }
}
