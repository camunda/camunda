/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.importing.EventProcessGatewayDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.service.es.EsBulkByScrollTaskActionProgressReporter;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.index.reindex.UpdateByQueryAction;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;

import java.text.MessageFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENT_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_ID;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@RequiredArgsConstructor
@Slf4j
public class EventProcessInstanceWriter {

  private static final Script VARIABLE_CLEAR_SCRIPT = new Script(
    MessageFormat.format("ctx._source.{0} = new ArrayList();\n", VARIABLES)
  );

  private final EventProcessInstanceIndex eventProcessInstanceIndex;
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final DateTimeFormatter dateTimeFormatter;

  private List<Object> gatewayLookup;

  public void setGatewayLookup(final List<EventProcessGatewayDto> gatewayLookup) {
    this.gatewayLookup = gatewayLookup.stream()
      .map(gateway -> objectMapper.convertValue(gateway, new TypeReference<Map>() {
      })).collect(Collectors.toList());
  }

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

  public void deleteInstancesThatEndedBefore(final OffsetDateTime endDate) {
    final String indexName = getIndexName();
    final String deletedItemName = "event process instances";
    final String deletedItemIdentifier = String.format(
      "%s in index %s that ended before %s", deletedItemName, indexName, endDate
    );
    log.info("Performing cleanup on {}", deletedItemIdentifier);

    final EsBulkByScrollTaskActionProgressReporter progressReporter = new EsBulkByScrollTaskActionProgressReporter(
      getClass().getName(), esClient, DeleteByQueryAction.NAME
    );
    try {
      progressReporter.start();
      final BoolQueryBuilder filterQuery = boolQuery()
        .filter(rangeQuery(END_DATE).lt(dateTimeFormatter.format(endDate)));

      ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient, filterQuery, deletedItemName, deletedItemIdentifier, false, indexName
      );
    } finally {
      progressReporter.stop();
    }

    log.info("Finished cleanup on {}", deletedItemIdentifier);
  }

  public void deleteVariablesOfInstancesThatEndedBefore(final OffsetDateTime endDate) {
    final String indexName = getIndexName();
    final String updateItem = String.format(
      "event process variables in index %s that ended before %s", indexName, endDate
    );
    log.info("Performing cleanup on {}", updateItem);

    final EsBulkByScrollTaskActionProgressReporter progressReporter = new EsBulkByScrollTaskActionProgressReporter(
      getClass().getName(), esClient, UpdateByQueryAction.NAME
    );
    try {
      progressReporter.start();

      final BoolQueryBuilder filterQuery = boolQuery()
        .filter(rangeQuery(END_DATE).lt(dateTimeFormatter.format(endDate)))
        .filter(nestedQuery(VARIABLES, existsQuery(VARIABLES + "." + VARIABLE_ID), ScoreMode.None));

      ElasticsearchWriterUtil.tryUpdateByQueryRequest(
        esClient, updateItem, VARIABLE_CLEAR_SCRIPT, filterQuery, indexName
      );
    } finally {
      progressReporter.stop();
    }

    log.info("Finished cleanup on {}", updateItem);
  }

  public void deleteEventsWithIdsInFromAllInstances(final List<String> eventIdsToDelete) {
    final String updateItem = String.format(
      "event instance events with ID from list of size %s",
      eventIdsToDelete.size()
    );

    final NestedQueryBuilder query = nestedQuery(
      EVENTS,
      termsQuery(EVENTS + "." + EVENT_ID, eventIdsToDelete.toArray()),
      ScoreMode.None
    );

    final Script deleteEventsScript = buildDeleteEventsWithIdsInScript(eventIdsToDelete);
    ElasticsearchWriterUtil.tryUpdateByQueryRequest(
      esClient, updateItem, deleteEventsScript, query, getIndexName()
    );
  }

  private Script buildDeleteEventsWithIdsInScript(final List<String> eventIdsToDelete) {
    final Map<String, Object> params = ImmutableMap.<String, Object>builder()
      .put("eventIdsToDelete", eventIdsToDelete)
      .build();
    return new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      MessageFormat.format(
        "ctx._source.{0}.removeIf(event -> params.eventIdsToDelete.contains(event.{1}));\n",
        EVENTS,
        EVENT_ID
      ),
      params
    );
  }

  private String getIndexName() {
    return eventProcessInstanceIndex.getIndexName();
  }

  private void addImportProcessInstanceRequest(final BulkRequest bulkRequest,
                                               final EventProcessInstanceDto eventProcessInstanceDto) {
    final Map<String, Object> params = new HashMap<>();
    params.put("processInstance", eventProcessInstanceDto);
    params.put("gatewayLookup", gatewayLookup);
    params.put("dateFormatPattern", OPTIMIZE_DATE_FORMAT);
    final Script updateScript = createDefaultScriptWithSpecificDtoParams(
      createUpdateInlineUpdateScript(),
      params,
      objectMapper
    );

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
      createRemoveExistingGatewaysFunction() +
      createNewGatewayFunction() +
      createAddGatewaysForProcessInstanceFunction() +
      addOpeningGatewayInstancesFunction() +
      addClosingGatewayInstancesFunction() +
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

      "removeExistingGateways(processInstance, params.gatewayLookup);\n" +
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
        // this check shouldn't be needed but exists as a safety net as activities can never be uncanceled
        "  if (event.canceled == false && eventUpsert.canceled == true) { \n" +
        "    event.canceled = eventUpsert.canceled;\n" +
        "  }\n" +
        "}\n" +
        "calculateAndAssignEventDuration(event, dateFormatter);\n" +
      "}\n" +

      "Map existingFlowNodeInstancesByActivityId = existingEventsByIdMap.values().stream()\n" +
        ".collect(Collectors.groupingBy(event -> event.activityId));\n" +
      "existingFlowNodeInstancesByActivityId\n" +
        ".values()\n" +
        ".forEach(byActivityIdList -> byActivityIdList.sort(eventDateComparator));\n" +
      "def correlatedEventsById = processInstance.correlatedEventsById;\n" +
      "processInstanceUpdate.correlatedEventsById.forEach((eventId, correlationStateUpdate) -> {\n" +
        "correlatedEventsById.putIfAbsent(eventId, correlationStateUpdate);\n" +
      "});\n" +
      "List pendingFlowNodeInstanceUpdates = new ArrayList(processInstance.pendingFlowNodeInstanceUpdates);\n" +
      "for (def newPendingUpdate : processInstanceUpdate.pendingFlowNodeInstanceUpdates) {\n" +
        "pendingFlowNodeInstanceUpdates.removeIf(existingUpdate -> existingUpdate.id.equals(newPendingUpdate.id));\n" +
        "pendingFlowNodeInstanceUpdates.add(newPendingUpdate);\n" +
      "}\n" +
      "Collections.sort(pendingFlowNodeInstanceUpdates, Comparator.comparing(flowNodeInstanceUpdate -> flowNodeInstanceUpdate.date, Comparator.naturalOrder()));\n" +
      "Set appliedUpdates = new HashSet();\n" +
      "for (def flowNodeInstanceUpdate : pendingFlowNodeInstanceUpdates) {\n" +
        "def correlatedEventState = correlatedEventsById.get(flowNodeInstanceUpdate.sourceEventId);\n" +
        "def correlatedAsInstanceIds = correlatedEventState.correlatedAsToFlowNodeInstanceIds\n" +
          ".computeIfAbsent(flowNodeInstanceUpdate.mappedAs, key -> new ArrayList());\n" +
        "def updateableFlowNodeInstance = Optional.ofNullable(existingFlowNodeInstancesByActivityId.get(flowNodeInstanceUpdate.flowNodeId))\n" +
          ".flatMap(flowNodeInstances -> flowNodeInstances.stream()\n" +
            ".filter(flowNodeInstance -> {\n" +
            "  return correlatedAsInstanceIds.contains(flowNodeInstance.id) || flowNodeInstance.startDate == null || flowNodeInstance.endDate == null\n" +
            "})\n" +
            ".findFirst()\n" +
          ");\n" +
        "if(updateableFlowNodeInstance.isPresent()) {\n" +
        "  def flowNodeInstanceToUpdate = updateableFlowNodeInstance.get();\n" +
        "  def wasAlreadyCorrelated = correlatedAsInstanceIds.contains(flowNodeInstanceToUpdate.id);\n" +
        // These 'if' statements are required to stop updates of the 'wrong' event in a looping sequence,
        // which would result in the start date being after the end date or vice versa
        "  def eventDate = dateFormatter.parse(flowNodeInstanceUpdate.date);\n" +
        "  if (flowNodeInstanceUpdate.mappedAs.equals(\"START\")\n" +
        "    && (wasAlreadyCorrelated || eventDate.before(dateFormatter.parse(flowNodeInstanceToUpdate.endDate)))) {\n" +
        "    flowNodeInstanceToUpdate.startDate = flowNodeInstanceUpdate.date;\n" +
        "    appliedUpdates.add(flowNodeInstanceUpdate);\n" +
        "  } else if (flowNodeInstanceUpdate.mappedAs.equals(\"END\")\n" +
        "    && (wasAlreadyCorrelated ||  eventDate.after(dateFormatter.parse(flowNodeInstanceToUpdate.startDate)))) {\n" +
        "    flowNodeInstanceToUpdate.endDate = flowNodeInstanceUpdate.date;\n" +
        "    appliedUpdates.add(flowNodeInstanceUpdate);\n" +
        "  }\n" +
        "  if (appliedUpdates.contains(flowNodeInstanceUpdate)) {\n" +
        "    if (!correlatedAsInstanceIds.contains(flowNodeInstanceToUpdate.id)) {\n" +
        "      correlatedAsInstanceIds.add(flowNodeInstanceToUpdate.id);\n" +
        "    }\n" +
        "    calculateAndAssignEventDuration(flowNodeInstanceToUpdate, dateFormatter);\n" +
        "  }\n" +
        "}\n" +
      "}\n" +

      "processInstance.events = new ArrayList(\n" +
        "existingFlowNodeInstancesByActivityId.values().stream().flatMap(List::stream).collect(Collectors.toList())\n" +
      ");\n" +
      "processInstance.pendingFlowNodeInstanceUpdates = pendingFlowNodeInstanceUpdates.stream()\n" +
        ".filter(flowNodeInstanceUpdate -> !appliedUpdates.contains(flowNodeInstanceUpdate))\n" +
        ".collect(Collectors.toList());\n" +

      "addGatewaysForProcessInstance(processInstance, params.gatewayLookup, eventDateComparator, processInstanceUpdate.processInstanceId, dateFormatter);\n" +
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

  private String createRemoveExistingGatewaysFunction() {
    // @formatter:off
    return
      "void removeExistingGateways(def instance, def gatewayLookup) {\n" +
        "gatewayLookup.forEach(gatewayEvent -> \n" +
          "instance.events.removeIf(event -> gatewayEvent.id.equals(event.activityId))\n" +
        ")" +
      "}\n";
    // @formatter:on
  }

  private String createAddGatewaysForProcessInstanceFunction() {
    // @formatter:off
    return
      "void addGatewaysForProcessInstance(def instance, def gatewayLookup, def startEndDateComparator, def processInstanceId, def dateFormatter) {\n" +
        "List gatewayEventsToAdd = new ArrayList();\n" +
        "List gatewayIdsInModel = gatewayLookup.stream().map(gateway -> gateway.id).collect(Collectors.toList());\n" +
        "List existingEvents = new ArrayList(instance.events);\n" +
        "if (!existingEvents.isEmpty() && !gatewayLookup.isEmpty()) { \n" +
          "Collections.sort(existingEvents, startEndDateComparator);\n" +
          "for (def possibleGateway : gatewayLookup) {\n" +
            "int eventAddedCount = 0;\n" +
            // Gateways with a single source flow node are opening gateways.
            "if (possibleGateway.previousNodeIds.size() == 1) {\n" +
              "addOpeningGatewayInstances(possibleGateway, gatewayIdsInModel, existingEvents, gatewayEventsToAdd," +
                                  "eventAddedCount, processInstanceId, dateFormatter);\n" +
            // Gateways with a single target flow node are closing gateways.
            "} else if (possibleGateway.nextNodeIds.size() == 1) {\n" +
              "addClosingGatewayInstances(possibleGateway, gatewayIdsInModel, existingEvents, gatewayEventsToAdd," +
                                  "eventAddedCount, processInstanceId, dateFormatter);\n" +
            "}\n" +
          "}\n" +
          "existingEvents.addAll(gatewayEventsToAdd);\n" +
          "Collections.sort(existingEvents, startEndDateComparator);\n" +
          "instance.events = existingEvents;\n" +
        "}\n" +
      "}\n";
  }

  private String addOpeningGatewayInstancesFunction() {
    // @formatter:off
    return
      "void addOpeningGatewayInstances(def possibleGateway, def gatewayIdsInModel, def existingEvents, def gatewayEventsToAdd," +
                             "def eventAddedCount, def processInstanceId, def dateFormatter) {\n" +
        // For event based gateways
        "if (possibleGateway.type.equals(\"eventBasedGateway\")) {\n" +
          "def previousNodeId = possibleGateway.previousNodeIds.get(0);\n" +
          // If the source flow node is also a gateway, we create a new gateway for each occurrence of one of the target flow
          //  node events, using the start date and end date of the target flow node event
          "if (gatewayIdsInModel.contains(previousNodeId)) {\n" +
            "for (def event : existingEvents) {\n" +
              "if (possibleGateway.nextNodeIds.contains(event.activityId) && event.startDate != null) {\n" +
                "eventAddedCount ++;\n" +
                "def newGateway = createNewGateway(possibleGateway.id, possibleGateway.type, \n" +
                                    "event.startDate, event.startDate, processInstanceId, eventAddedCount);\n" +
                "gatewayEventsToAdd.add(newGateway);\n" +
              "}\n" +
            "}\n" +
          // If the source flow node is not a gateway, we create a new gateway for each occurrence of one of the target flow node
          //  events, using the end date of the source flow node event and the start date of the target flow node event
          "} else {\n" +
            "List targetEvents = existingEvents.stream()" +
               ".filter(event -> possibleGateway.nextNodeIds.contains(event.activityId) && event.startDate != null)\n" +
               ".collect(Collectors.toList());\n" +
            "List sourceEvents = existingEvents.stream()" +
               ".filter(event -> possibleGateway.previousNodeIds.get(0).equals(event.activityId) && event.endDate != null)\n" +
               ".collect(Collectors.toList());\n" +
            "boolean gatewayCanBeAdded = !sourceEvents.isEmpty() && !targetEvents.isEmpty();\n" +
            "while (gatewayCanBeAdded) {\n" +
              "def sourceEvent = sourceEvents.remove(0);\n" +
              "def targetEvent = targetEvents.remove(0);\n" +
              "eventAddedCount ++;\n" +
              "def newGateway = createNewGateway(possibleGateway.id, possibleGateway.type, \n" +
                                  "sourceEvent.endDate, targetEvent.startDate, processInstanceId, eventAddedCount);\n" +
              "calculateAndAssignEventDuration(newGateway, dateFormatter);\n" +
              "gatewayEventsToAdd.add(newGateway);\n" +
              "gatewayCanBeAdded = !sourceEvents.isEmpty() && !targetEvents.isEmpty();\n" +
            "}\n" +
          "}\n" +

        // For exclusive and parallel gateways
        // A gateway with a single source flow node that isn't a gateway can be added for every occurrence of that flow node event
        "} else if (!gatewayIdsInModel.contains(possibleGateway.previousNodeIds.get(0))) {" +
          "for (def event : existingEvents) {\n" +
            "if (possibleGateway.previousNodeIds.contains(event.activityId) && event.endDate != null) {\n" +
              "eventAddedCount ++;\n" +
              "def newGateway = createNewGateway(possibleGateway.id, possibleGateway.type, \n" +
                                  "event.endDate, event.endDate, processInstanceId, eventAddedCount);\n" +
              "gatewayEventsToAdd.add(newGateway);\n" +
            "}\n" +
          "}\n" +
        // A gateway with source flow node as a gateway can be added if the expected target flow node event(s) have occurred
        "} else {\n" +
          // For any gateway type, this is when any one of its target flow node events have occurred
          "for (def event : existingEvents) {\n" +
            "if (possibleGateway.nextNodeIds.contains(event.activityId)) {\n" +
              "eventAddedCount ++;\n" +
              "def newGateway = createNewGateway(possibleGateway.id, possibleGateway.type, \n" +
                                  "event.startDate, event.startDate, processInstanceId, eventAddedCount);\n" +
              "gatewayEventsToAdd.add(newGateway);\n" +
            "}\n" +
          "}\n" +
        "}\n" +
      "}\n";
    // @formatter:on
  }

  private String addClosingGatewayInstancesFunction() {
    // @formatter:off
    return
      "void addClosingGatewayInstances(def possibleGateway, def gatewayIdsInModel, def existingEvents, def gatewayEventsToAdd," +
                             "def eventAddedCount, def processInstanceId, def dateFormatter) {\n" +
        // For exclusive gateways
        "if (possibleGateway.type.equals(\"exclusiveGateway\")) {\n" +
          // if the target flow node event is not a gateway, we can add an exclusive gateway for every
          //  occurrence of the target flow node event
          "if (!gatewayIdsInModel.contains(possibleGateway.nextNodeIds.get(0))) {\n" +
            "for (def event : existingEvents) {\n" +
              "if (possibleGateway.nextNodeIds.contains(event.activityId)) {\n" +
                "eventAddedCount ++;\n" +
                "def newGateway = createNewGateway(possibleGateway.id, possibleGateway.type, \n" +
                                    "event.startDate, event.startDate, processInstanceId, eventAddedCount);\n" +
                "gatewayEventsToAdd.add(newGateway);\n" +
              "}\n" +
            "}\n" +
          // if the target flow node event is a gateway, we can add an exclusive gateway for every occurrence
          //  of one of the source flow node events
          "} else {" +
            "for (def event : existingEvents) {\n" +
              "if (possibleGateway.previousNodeIds.contains(event.activityId) && event.endDate != null) {\n" +
                "eventAddedCount ++;\n" +
                "def newGateway = createNewGateway(possibleGateway.id, possibleGateway.type, \n" +
                                    "event.endDate, event.endDate, processInstanceId, eventAddedCount);\n" +
                "gatewayEventsToAdd.add(newGateway);\n" +
              "}\n" +
            "}\n" +
          "}\n" +

        // For parallel gateways
        "} else if (possibleGateway.type.equals(\"parallelGateway\")) {\n" +
          "Map relatedEventsByActivityId = existingEvents.stream()\n" +
                                 ".filter(event -> possibleGateway.previousNodeIds.contains(event.activityId) && event.endDate != null)\n" +
                                 ".collect(Collectors.groupingBy(event -> event.activityId));\n" +
          "List mappablePreviousNodeIds = possibleGateway.previousNodeIds.stream()" +
            ".filter(nodeId -> !gatewayIdsInModel.contains(nodeId))\n" +
            ".collect(Collectors.toList());\n" +
          // We add a parallel gateway for every occurrence of all mappable source flow node events
          "boolean gatewayCanBeAdded = relatedEventsByActivityId.keySet().containsAll(mappablePreviousNodeIds);\n" +
          "while (gatewayCanBeAdded) {\n" +
            "List eventsForGateway = new ArrayList();\n" +
            "for (def previousNodeId : mappablePreviousNodeIds) {\n" +
              "eventsForGateway.add(relatedEventsByActivityId.get(previousNodeId).remove(0));\n" +
            "}\n" +
            "def endDateComparator = Comparator.comparing(event -> event.endDate, Comparator.nullsLast(Comparator.naturalOrder()));\n" +
            "Collections.sort(eventsForGateway, endDateComparator);\n" +
            "def firstEventForGateway = eventsForGateway.get(0);" +
            "def lastEventForGateway = eventsForGateway.get(eventsForGateway.size() - 1);" +
            "eventAddedCount ++;\n" +
            "def newGateway = createNewGateway(possibleGateway.id, possibleGateway.type, \n" +
                                  "firstEventForGateway.endDate, lastEventForGateway.endDate, processInstanceId, eventAddedCount);\n" +
            "calculateAndAssignEventDuration(newGateway, dateFormatter);\n" +
            "gatewayEventsToAdd.add(newGateway);\n" +
            "gatewayCanBeAdded = !relatedEventsByActivityId.values().stream().anyMatch(eventsForActivity -> eventsForActivity.isEmpty());\n" +
          "}\n" +
        "}\n" +
      "}\n";
    // @formatter:on
  }

  private String createNewGatewayFunction() {
    // @formatter:off
    return
      "def createNewGateway(def activityId, def activityType, def startDate, def endDate, def processInstanceId, def eventAddedCount) {\n" +
        "def newGateway = [\n" +
          "'id': activityId + '_' + eventAddedCount,\n" +
          "'activityId': activityId,\n" +
          "'activityType': activityType,\n" +
          "'durationInMs': 0,\n" +
          "'startDate': startDate,\n" +
          "'endDate': endDate,\n" +
          "'processInstanceId': processInstanceId\n" +
        "];\n" +
        "return newGateway;\n" +
      "}\n";
    // @formatter:on
  }

}
