package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.SimpleEventDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;


@Component
public abstract class AbstractActivityInstanceWriter {
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  public void importActivityInstances(List<FlowNodeEventDto> events) throws Exception {
    logger.debug("Writing [{}] activity instances to elasticsearch", events.size());

    BulkRequestBuilder addEventToProcessInstanceBulkRequest = esclient.prepareBulk();
    Map<String, List<FlowNodeEventDto>> processInstanceToEvents = new HashMap<>();
    for (FlowNodeEventDto e : events) {
      if (!processInstanceToEvents.containsKey(e.getProcessInstanceId())) {
        processInstanceToEvents.put(e.getProcessInstanceId(), new ArrayList<>());
      }
      processInstanceToEvents.get(e.getProcessInstanceId()).add(e);
    }

    for (Map.Entry<String, List<FlowNodeEventDto>> entry : processInstanceToEvents.entrySet()) {
      addActivityInstancesToProcessInstanceRequest(
        addEventToProcessInstanceBulkRequest,
        entry.getValue(),
        entry.getKey()
      );
    }

    BulkResponse response = addEventToProcessInstanceBulkRequest.get();
    if (response.hasFailures()) {
      String errorMessage = String.format(
        "There were failures while writing activity instance with message: %s",
        response.buildFailureMessage()
      );
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  private void addActivityInstancesToProcessInstanceRequest(
      BulkRequestBuilder addEventToProcessInstanceBulkRequest,
      List<FlowNodeEventDto> processEvents, String processInstanceId) throws IOException {

    List<SimpleEventDto> simpleEvents = getSimpleEventDtos(processEvents);
    Map<String, Object> params = new HashMap<>();
    // see https://discuss.elastic.co/t/how-to-update-nested-objects-in-elasticsearch-2-2-script-via-java-api/43135
    List jsonMap = objectMapper.readValue(
      objectMapper.writeValueAsString(simpleEvents),
      List.class
    );
    params.put(EVENTS, jsonMap);

    Script updateScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      createInlineUpdateScript(),
      params
    );

    FlowNodeEventDto e = getFirst(processEvents);
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(e.getProcessDefinitionId());
    procInst.setProcessDefinitionKey(e.getProcessDefinitionKey());
    procInst.setProcessInstanceId(e.getProcessInstanceId());
    procInst.getEvents().addAll(simpleEvents);
    procInst.setEngine(e.getEngineAlias());
    String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);

    addEventToProcessInstanceBulkRequest.add(esclient
      .prepareUpdate(
        getOptimizeIndexAliasForType(configurationService.getProcessInstanceType()),
        configurationService.getProcessInstanceType(),
        processInstanceId
      )
      .setScript(updateScript)
      .setUpsert(newEntryIfAbsent, XContentType.JSON)
      .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict())
    );
  }

  protected abstract String createInlineUpdateScript();

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
      simpleEventDto.setStartDate(e.getStartDate());
      simpleEventDto.setEndDate(e.getEndDate());
      simpleEvents.add(simpleEventDto);
    }
    return simpleEvents;
  }

}