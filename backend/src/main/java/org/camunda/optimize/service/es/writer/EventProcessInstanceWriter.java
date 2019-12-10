/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@AllArgsConstructor
@Slf4j
public class EventProcessInstanceWriter {
  private final EventProcessInstanceIndex eventProcessInstanceIndex;
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void importProcessInstances(final List<ProcessInstanceDto> processInstanceDtos) {
    String importItemName = "event process instances";
    log.debug("Writing [{}] {} to ES.", processInstanceDtos.size(), importItemName);

    ElasticsearchWriterUtil.doBulkRequestWithList(
      esClient,
      importItemName,
      processInstanceDtos,
      this::addImportProcessInstanceRequest
    );
  }

  private String getIndexName() {
    return eventProcessInstanceIndex.getIndexName();
  }

  private String createInlineUpdateScript() {
    // @formatter:off
    return
      "for (def variableEntry : params.processInstance.variables) {" +
        "ctx._source.variables.removeIf(item -> item.id.equals(variableEntry.id));" +
      "}" +
      "ctx._source.variables.addAll(params.processInstance.variables);" +

      "for (def newEvent : params.processInstance.events) {" +
        "ctx._source.events.removeIf(item -> item.id.equals(newEvent.id));" +
      "}" +
      "ctx._source.events.addAll(params.processInstance.events);" +

      "def startDate = ctx._source.events.stream()" +
        ".filter(event -> event.activityType.equals(\"startEvent\"))" +
        ".map(event -> event.startDate)" +
        ".sorted()" +
        ".findFirst();" +
      "startDate.ifPresent(value -> ctx._source.startDate = value);" +
      "def endDate = ctx._source.events.stream()" +
        ".filter(event -> event.activityType.equals(\"endEvent\"))" +
        ".map(event -> event.endDate)" +
        ".sorted(Comparator.reverseOrder())" +
        ".findFirst();" +
      "ctx._source.state = \"ACTIVE\";" +
      "endDate.ifPresent(endDateValue -> {" +
        "ctx._source.endDate = endDateValue;" +
        "ctx._source.state = \"COMPLETED\";" +
        "def dateFormatter = new SimpleDateFormat(params.dateFormatPattern);\n" +
        "startDate.ifPresent(" +
        "  startDateValue -> ctx._source.duration = " +
        "    dateFormatter.parse(endDateValue).getTime() - dateFormatter.parse(startDateValue).getTime()" +
        ");" +
      "});"
      ;
    // @formatter:on
  }

  private void addImportProcessInstanceRequest(BulkRequest bulkRequest,
                                               ProcessInstanceDto processInstanceDto) {
    final Map<String, Object> params = new HashMap<>();
    params.put(
      "processInstance",
      // @formatter:off
      objectMapper.convertValue(processInstanceDto, new TypeReference<Map>() {})
      // @formatter:on
    );
    params.put("dateFormatPattern", OPTIMIZE_DATE_FORMAT);
    final Script updateScript = createDefaultScript(createInlineUpdateScript(), params);

    String newEntryIfAbsent = "";
    try {
      newEntryIfAbsent = objectMapper.writeValueAsString(processInstanceDto);
    } catch (JsonProcessingException e) {
      String reason =
        String.format(
          "Error while processing JSON for process instance dto with [%s].", processInstanceDto.toString()
        );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    UpdateRequest request = new UpdateRequest()
      .index(getIndexName())
      .id(processInstanceDto.getProcessInstanceId())
      .script(updateScript)
      .scriptedUpsert(true)
      .upsert(newEntryIfAbsent, XContentType.JSON)
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(request);
  }

}