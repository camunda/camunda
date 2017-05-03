package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.SimpleEventDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
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
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  public void importEvents(List<EventDto> events) throws Exception {
    logger.debug("Writing [{}] events to elasticsearch", events.size());

    BulkRequestBuilder addEventToProcessInstanceBulkRequest = esclient.prepareBulk();
    BulkRequestBuilder eventBulkRequest = esclient.prepareBulk();
    for (EventDto e : events) {
      addEventToProcessInstanceRequest(addEventToProcessInstanceBulkRequest, e);
      addEventRequest(eventBulkRequest, e);
    }
    addEventToProcessInstanceBulkRequest.get();
    eventBulkRequest.get();
  }

  private void addEventRequest(BulkRequestBuilder eventBulkRequest, EventDto e) {
    eventBulkRequest.add(
      esclient.prepareIndex(
        configurationService.getOptimizeIndex(),
        configurationService.getEventType(),
        e.getId())
      .setSource(Collections.emptyMap())
    );
  }

  private void addEventToProcessInstanceRequest(BulkRequestBuilder addEventToProcessInstanceBulkRequest, EventDto e) throws IOException {
    String processInstanceId = e.getProcessInstanceId();
    SimpleEventDto simpleEventDto = new SimpleEventDto();
    simpleEventDto.setDurationInMs(e.getDurationInMs());
    simpleEventDto.setActivityId(e.getActivityId());
    simpleEventDto.setId(e.getId());
    simpleEventDto.setActivityType(e.getActivityType());
    Map<String, Object> params = new HashMap<>();
    // see https://discuss.elastic.co/t/how-to-update-nested-objects-in-elasticsearch-2-2-script-via-java-api/43135
    HashMap jsonMap = objectMapper.readValue(
      objectMapper.writeValueAsString(simpleEventDto),
      HashMap.class
    );
    params.put("event", jsonMap);

    Script updateScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.events.add(params.event)",
      params
    );

    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(e.getProcessDefinitionId());
    procInst.setProcessDefinitionKey(e.getProcessDefinitionKey());
    procInst.setProcessInstanceId(e.getProcessInstanceId());
    procInst.setStartDate(new Date());
    procInst.setEndDate(new Date());
    procInst.getEvents().add(simpleEventDto);
    String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);

    addEventToProcessInstanceBulkRequest.add(esclient
      .prepareUpdate(
        configurationService.getOptimizeIndex(),
        configurationService.getProcessInstanceType(),
        processInstanceId)
      .setScript(updateScript)
      .setUpsert(newEntryIfAbsent, XContentType.JSON)
    );
  }

}