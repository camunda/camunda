package org.camunda.optimize.mapper;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceDto;
import org.camunda.optimize.dto.optimize.EventTO;

import java.util.Collection;

/**
 * @author Askar Akhmerov
 */
public class EventMappingHelper {

  private static final String STATE_COMPLETED = "COMPLETED";
  private static final String STATE_CREATED = "CREATED";

  public static void map(HistoricActivityInstanceDto dto, Collection<EventTO> result) {
    final EventTO createEvent = new EventTO();
    createEvent.setState(STATE_CREATED);
    mapDefaults(dto, createEvent);

    result.add(createEvent);

    if (dto.getEndTime() != null) {
      final EventTO completeEvent = new EventTO();
      completeEvent.setState(STATE_COMPLETED);
      mapDefaults(dto, createEvent);

      result.add(completeEvent);
    }
  }

  private static void mapDefaults(HistoricActivityInstanceDto dto, EventTO createEvent) {
    createEvent.setActivityId(dto.getActivityId());
    createEvent.setActivityInstanceId(dto.getParentActivityInstanceId());
    createEvent.setTimestamp(dto.getStartTime());
    createEvent.setProcessDefinitionKey(dto.getProcessDefinitionKey());
    createEvent.setProcessInstanceId(dto.getProcessInstanceId());
  }
}
