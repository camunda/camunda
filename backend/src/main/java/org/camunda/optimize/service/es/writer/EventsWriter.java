package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.SimpleEventDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
@Component
public class EventsWriter {
  private final Logger logger = LoggerFactory.getLogger(EventsWriter.class);

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  public void importEvents(List<FlowNodeEventDto> events) throws Exception {
    logger.debug("Writing [{}] events to elasticsearch", events.size());

    BulkRequestBuilder addEventToProcessInstanceBulkRequest = esclient.prepareBulk();
    BulkRequestBuilder eventBulkRequest = esclient.prepareBulk();
    Map<String, List<FlowNodeEventDto>> processInstanceToEvents = new HashMap<>();
    for (FlowNodeEventDto e : events) {
      if (!processInstanceToEvents.containsKey(e.getProcessInstanceId())) {
        processInstanceToEvents.put(e.getProcessInstanceId(), new ArrayList<>());
      }
      processInstanceToEvents.get(e.getProcessInstanceId()).add(e);
      addEventRequest(eventBulkRequest, e);
    }

    for (Map.Entry<String, List<FlowNodeEventDto>> entry : processInstanceToEvents.entrySet()) {
        addEventsToProcessInstanceRequest(addEventToProcessInstanceBulkRequest, entry.getValue(), entry.getKey());
    }
    BulkResponse response = addEventToProcessInstanceBulkRequest.get();
    if (response.hasFailures()) {
      logger.warn("There were failures while writing events with message: {}", response.buildFailureMessage());
    }
    eventBulkRequest.get();
  }

  private void addEventRequest(BulkRequestBuilder eventBulkRequest, FlowNodeEventDto e) {
    eventBulkRequest.add(
        esclient.prepareIndex(
            configurationService.getOptimizeIndex(configurationService.getEventType()),
            configurationService.getEventType(),
            e.getId()
        )
        .setSource(Collections.emptyMap())
    );
  }

  private void addEventsToProcessInstanceRequest(
    BulkRequestBuilder addEventToProcessInstanceBulkRequest,
    List<FlowNodeEventDto> processEvents, String processInstanceId) throws IOException {

    List<SimpleEventDto> simpleEvents = getSimpleEventDtos(processEvents);
    Map<String, Object> params = new HashMap<>();
    // see https://discuss.elastic.co/t/how-to-update-nested-objects-in-elasticsearch-2-2-script-via-java-api/43135
    List jsonMap = objectMapper.readValue(
        objectMapper.writeValueAsString(simpleEvents),
        List.class
    );
    params.put("events", jsonMap);

    Script updateScript = new Script(
        ScriptType.INLINE,
        Script.DEFAULT_SCRIPT_LANG,
        "ctx._source.events.addAll(params.events)",
        params
    );

    FlowNodeEventDto e = getFirst(processEvents);
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(e.getProcessDefinitionId());
    procInst.setProcessDefinitionKey(e.getProcessDefinitionKey());
    procInst.setProcessInstanceId(e.getProcessInstanceId());
    procInst.getEvents().addAll(simpleEvents);
    String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);

    addEventToProcessInstanceBulkRequest.add(esclient
        .prepareUpdate(
            configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()),
            configurationService.getProcessInstanceType(),
            processInstanceId
        )
        .setScript(updateScript)
        .setUpsert(newEntryIfAbsent, XContentType.JSON)
        .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict())
    );
  }

  private FlowNodeEventDto getFirst(List<FlowNodeEventDto> processEvents) {
    return processEvents.get(0);
  }

  private List<SimpleEventDto> getSimpleEventDtos(List<FlowNodeEventDto> processEvents) {
    List<SimpleEventDto> simpleEvents = new ArrayList<>();
    for (FlowNodeEventDto e : processEvents) {
      SimpleEventDto simpleEventDto = new SimpleEventDto();
      simpleEventDto.setDurationInMs(e.getDurationInMs());
      simpleEventDto.setActivityId(e.getActivityId());
      simpleEventDto.setId(e.getId());
      simpleEventDto.setActivityType(e.getActivityType());
      simpleEvents.add(simpleEventDto);
    }
    return simpleEvents;
  }

}