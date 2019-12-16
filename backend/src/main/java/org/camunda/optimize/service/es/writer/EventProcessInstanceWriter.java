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
import org.camunda.optimize.dto.optimize.persistence.EventProcessInstanceDto;
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

  public void importProcessInstances(final List<EventProcessInstanceDto> eventProcessInstanceDtos) {
    final String importItemName = "event process instances";
    log.debug("Writing [{}] {} to ES.", eventProcessInstanceDtos.size(), importItemName);

    ElasticsearchWriterUtil.doBulkRequestWithList(
      esClient,
      importItemName,
      eventProcessInstanceDtos,
      this::addImportProcessInstanceRequest
    );
  }

  private String getIndexName() {
    return eventProcessInstanceIndex.getIndexName();
  }

  private void addImportProcessInstanceRequest(final BulkRequest bulkRequest,
                                               final EventProcessInstanceDto eventProcessInstanceDto) {
    final Map<String, Object> params = new HashMap<>();
    params.put(
      "processInstance",
      // @formatter:off
      objectMapper.convertValue(eventProcessInstanceDto, new TypeReference<Map>() {})
      // @formatter:on
    );
    params.put("dateFormatPattern", OPTIMIZE_DATE_FORMAT);
    final Script updateScript = createDefaultScript(createUpdateInlineUpdateScript(), params);

    final String newEntryIfAbsent;
    try {
      newEntryIfAbsent = objectMapper.writeValueAsString(eventProcessInstanceDto);
    } catch (JsonProcessingException e) {
      String reason =
        String.format(
          "Error while processing JSON for process instance dto with [%s].", eventProcessInstanceDto.toString()
        );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    UpdateRequest request = new UpdateRequest()
      .index(getIndexName())
      .id(eventProcessInstanceDto.getProcessInstanceId())
      .upsert(newEntryIfAbsent, XContentType.JSON)
      .script(updateScript)
      .scriptedUpsert(true)
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(request);
  }

  private String createUpdateInlineUpdateScript() {
    // @formatter:off
    return
      createInstanceUpdateFunction() +
      "void calculateAndAssignEventDuration(def event, def formatter) {\n" +
        "if (event.startDate != null && event.endDate != null) {\n" +
        "  event.durationInMs = formatter.parse(event.endDate).getTime() - formatter.parse(event.startDate).getTime();\n" +
        "}\n" +
      "}\n" +

      "def dateFormatter = new SimpleDateFormat(params.dateFormatPattern);\n" +
      "def processInstance = ctx._source;\n" +
      "def processInstanceUpdate = params.processInstance;\n" +
      "def eventDateComparator = Comparator\n" +
        ".comparing(event -> event.startDate, Comparator.nullsLast(Comparator.naturalOrder()))" +
        ".thenComparing(event -> event.endDate, Comparator.nullsFirst(Comparator.naturalOrder()));\n" +

      "for (def variableEntry : processInstanceUpdate.variables) {\n" +
        "processInstance.variables.removeIf(item -> item.id.equals(variableEntry.id));\n" +
      "}\n" +
      "processInstance.variables.addAll(processInstanceUpdate.variables);\n" +

      "Map existingEventsByIdMap = processInstance.events.stream()\n" +
        ".collect(Collectors.toMap(event -> event.id, Function.identity()));\n" +
      "def eventUpserts = processInstanceUpdate.events;\n" +
      "for (def eventUpsert : eventUpserts) {\n" +
        "def event = existingEventsByIdMap.get(eventUpsert.id);\n" +
        "if (event == null) {\n" +
        "  existingEventsByIdMap.put(eventUpsert.id, eventUpsert);\n" +
        "  event = eventUpsert;\n" +
        "} else {\n" +
        "  event.startDate = eventUpsert.startDate ?: event.startDate;\n" +
        "  event.endDate = eventUpsert.endDate ?: event.endDate;\n" +
        "}\n" +
        "calculateAndAssignEventDuration(event, dateFormatter);\n" +
      "}\n" +

      "Map existingFlowNodeInstancesByActivityId = existingEventsByIdMap.values().stream()\n" +
        ".collect(Collectors.groupingBy(" +
          "event -> event.activityId," +
          "Collectors.toCollection(() -> new TreeSet(eventDateComparator))" +
        "));\n" +
      "List pendingFlowNodeInstanceUpdates = new ArrayList(processInstance.pendingFlowNodeInstanceUpdates);\n" +
      "for (def newPendingUpdate : processInstanceUpdate.pendingFlowNodeInstanceUpdates) {\n" +
        "pendingFlowNodeInstanceUpdates.removeIf(existingUpdate -> existingUpdate.id.equals(newPendingUpdate.id));\n" +
        "pendingFlowNodeInstanceUpdates.add(newPendingUpdate);\n" +
      "}\n" +
      "Collections.sort(pendingFlowNodeInstanceUpdates, eventDateComparator);\n" +
      "Set appliedUpdates = new HashSet();\n" +
      "for (def eventUpdate : pendingFlowNodeInstanceUpdates) {\n" +
        "def updateableEvent = Optional.ofNullable(existingFlowNodeInstancesByActivityId.get(eventUpdate.flowNodeId))" +
          ".flatMap(events -> \n" +
            "events.stream()\n" +
              ".filter(event -> event.startDate == null || event.endDate == null)\n" +
              ".findFirst()\n" +
          ");\n" +
        "if(updateableEvent.isPresent()) {\n" +
        "  def event = updateableEvent.get();\n" +
        "  event.startDate = eventUpdate.startDate ?: event.startDate;\n" +
        "  event.endDate = eventUpdate.endDate ?: event.endDate;\n" +
        "  appliedUpdates.add(eventUpdate);\n" +
        "  calculateAndAssignEventDuration(event, dateFormatter);\n" +
        "}\n" +
      "}\n" +

      "processInstance.events = new ArrayList(\n" +
        "existingFlowNodeInstancesByActivityId.values().stream().flatMap(List::stream).collect(Collectors.toList())\n" +
      ");\n" +
      "processInstance.pendingFlowNodeInstanceUpdates = pendingFlowNodeInstanceUpdates.stream()\n" +
        ".filter(eventUpdate -> !appliedUpdates.contains(eventUpdate))\n" +
        ".collect(Collectors.toList());\n" +
      "updateProcessInstance(processInstance, dateFormatter);\n"
      ;
    // @formatter:on
  }

  private String createInstanceUpdateFunction() {
    // @formatter:off
    return
      "void updateProcessInstance(def instance, def formatter) {\n" +
        "def startDate = instance.events.stream()\n" +
        "  .filter(event -> event.activityType.equals(\"startEvent\"))\n" +
        "  .map(event -> event.startDate)\n" +
        "  .filter(value -> value != null)\n" +
        "  .sorted()\n" +
        "  .findFirst()\n" +
        "  .ifPresent(value -> instance.startDate = value);\n" +
        "def endDate = instance.events.stream()\n" +
        "  .filter(event -> event.activityType.equals(\"endEvent\"))\n" +
        "  .map(event -> event.endDate)\n" +
        "  .filter(value -> value != null)\n" +
        "  .sorted(Comparator.reverseOrder())\n" +
        "  .findFirst()\n" +
        "  .ifPresent(value -> instance.endDate = value);\n" +

        "if(instance.endDate != null) {\n" +
        "  instance.state = \"COMPLETED\";\n" +
        "} else {" +
        "  instance.state = \"ACTIVE\";\n" +
        "}\n" +

        "if (instance.startDate != null && instance.endDate != null) {\n" +
        "  instance.duration = formatter.parse(instance.endDate).getTime() - formatter.parse(instance.startDate).getTime();\n" +
        "}" +
      "}\n";
    // @formatter:on
  }
}
