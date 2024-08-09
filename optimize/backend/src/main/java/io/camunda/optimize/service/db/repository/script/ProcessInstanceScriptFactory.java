/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.script;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;

import java.text.MessageFormat;

public interface ProcessInstanceScriptFactory {
  static String createDeleteEventsWithIdsInScript() {
    return MessageFormat.format(
        "ctx._source.{0}.removeIf(event -> params.eventIdsToDelete.contains(event.{1}));\n",
        FLOW_NODE_INSTANCES, FLOW_NODE_INSTANCE_ID);
  }

  static String createVariableClearScript() {
    return MessageFormat.format("ctx._source.{0} = new ArrayList();\n", VARIABLES);
  }

  static String createInlineUpdateScript() {
    // @formatter:off
    return "if ((ctx._source.state == params.activeState || ctx._source.state == params.suspendedState) "
        + "&& (params.newState.equals(params.activeState) || params.newState.equals(params.suspendedState))) {"
        + "ctx._source.state = params.newState;"
        + "}\n";
    // @formatter:on
  }

  static String createEventInlineUpdateScript() {
    // @formatter:off
    return createInstanceUpdateFunction()
        + createRemoveExistingGatewaysFunction()
        + createNewGatewayFunction()
        + createAddGatewaysForProcessInstanceFunction()
        + addOpeningGatewayInstancesFunction()
        + addClosingGatewayInstancesFunction()
        + "void calculateAndAssignEventDuration(def event, def formatter) {\n"
        + "if (event.startDate != null && event.endDate != null) {\n"
        + "  event.totalDurationInMs = formatter.parse(event.endDate).getTime() - formatter.parse(event.startDate).getTime();\n"
        + "}\n"
        + "}\n"
        + "def dateFormatter = new SimpleDateFormat(params.dateFormatPattern);\n"
        + "def processInstance = ctx._source;\n"
        + "def processInstanceUpdate = params.processInstance;\n"
        + "def eventDateComparator = Comparator\n"
        + ".comparing(event -> event.startDate, Comparator.nullsLast(Comparator.naturalOrder()))"
        + ".thenComparing(event -> event.endDate, Comparator.nullsFirst(Comparator.naturalOrder()));\n"
        + "for (def variableEntry : processInstanceUpdate.variables) {\n"
        + "processInstance.variables.removeIf(item -> item.id.equals(variableEntry.id));\n"
        + "}\n"
        + "processInstance.variables.addAll(processInstanceUpdate.variables);\n"
        + "removeExistingGateways(processInstance, params.gatewayLookup);\n"
        + "Map existingEventsByIdMap = processInstance.flowNodeInstances.stream()\n"
        + ".collect(Collectors.toMap(event -> event.flowNodeInstanceId, Function.identity()));\n"
        + // Note that we can rely on FlowNodeInstanceId as identifier as there are no userTasks in
        // event based processes.
        "def eventUpserts = processInstanceUpdate.flowNodeInstances;\n"
        + "for (def eventUpsert : eventUpserts) {\n"
        + "def event = existingEventsByIdMap.get(eventUpsert.flowNodeInstanceId);\n"
        + "if (event == null) {\n"
        + "  existingEventsByIdMap.put(eventUpsert.flowNodeInstanceId, eventUpsert);\n"
        + "  event = eventUpsert;\n"
        + "} else {\n"
        + "  event.startDate = eventUpsert.startDate ?: event.startDate;\n"
        + "  event.endDate = eventUpsert.endDate ?: event.endDate;\n"
        +
        // this check shouldn't be needed but exists as a safety net as activities can never be
        // uncanceled
        "  if (event.canceled == false && eventUpsert.canceled == true) { \n"
        + "    event.canceled = eventUpsert.canceled;\n"
        + "  }\n"
        + "}\n"
        + "calculateAndAssignEventDuration(event, dateFormatter);\n"
        + "}\n"
        + "Map existingFlowNodeInstancesByFlowNodeId = existingEventsByIdMap.values().stream()\n"
        + ".collect(Collectors.groupingBy(event -> event.flowNodeId));\n"
        + "existingFlowNodeInstancesByFlowNodeId\n"
        + ".values()\n"
        + ".forEach(byFlowNodeIdList -> byFlowNodeIdList.sort(eventDateComparator));\n"
        + "def correlatedEventsById = processInstance.correlatedEventsById;\n"
        + "processInstanceUpdate.correlatedEventsById.forEach((eventId, correlationStateUpdate) -> {\n"
        + "correlatedEventsById.putIfAbsent(eventId, correlationStateUpdate);\n"
        + "});\n"
        + "List pendingFlowNodeInstanceUpdates = new ArrayList(processInstance.pendingFlowNodeInstanceUpdates);\n"
        + "for (def newPendingUpdate : processInstanceUpdate.pendingFlowNodeInstanceUpdates) {\n"
        + "pendingFlowNodeInstanceUpdates.removeIf(existingUpdate -> existingUpdate.id.equals(newPendingUpdate.id));\n"
        + "pendingFlowNodeInstanceUpdates.add(newPendingUpdate);\n"
        + "}\n"
        + "Collections.sort(pendingFlowNodeInstanceUpdates, Comparator.comparing(flowNodeInstanceUpdate -> flowNodeInstanceUpdate.date, Comparator.naturalOrder()));\n"
        + "Set appliedUpdates = new HashSet();\n"
        + "for (def flowNodeInstanceUpdate : pendingFlowNodeInstanceUpdates) {\n"
        + "def correlatedEventState = correlatedEventsById.get(flowNodeInstanceUpdate.sourceEventId);\n"
        + "def correlatedAsInstanceIds = correlatedEventState.correlatedAsToFlowNodeInstanceIds\n"
        + ".computeIfAbsent(flowNodeInstanceUpdate.mappedAs, key -> new ArrayList());\n"
        + "def updateableFlowNodeInstance = Optional.ofNullable(existingFlowNodeInstancesByFlowNodeId.get(flowNodeInstanceUpdate.flowNodeId))\n"
        + ".flatMap(flowNodeInstances -> flowNodeInstances.stream()\n"
        + ".filter(flowNodeInstance -> {\n"
        + "  return correlatedAsInstanceIds.contains(flowNodeInstance.flowNodeInstanceId) || flowNodeInstance.startDate == null || flowNodeInstance.endDate == null\n"
        + "})\n"
        + ".findFirst()\n"
        + ");\n"
        + "if(updateableFlowNodeInstance.isPresent()) {\n"
        + "  def flowNodeInstanceToUpdate = updateableFlowNodeInstance.get();\n"
        + "  def wasAlreadyCorrelated = correlatedAsInstanceIds.contains(flowNodeInstanceToUpdate.flowNodeInstanceId);\n"
        +
        // These 'if' statements are required to stop updates of the 'wrong' event in a looping
        // sequence,
        // which would result in the start date being after the end date or vice versa
        "  def eventDate = dateFormatter.parse(flowNodeInstanceUpdate.date);\n"
        + "  if (flowNodeInstanceUpdate.mappedAs.equals(\"START\")\n"
        + "    && (wasAlreadyCorrelated || eventDate.before(dateFormatter.parse(flowNodeInstanceToUpdate.endDate)))) {\n"
        + "    flowNodeInstanceToUpdate.startDate = flowNodeInstanceUpdate.date;\n"
        + "    appliedUpdates.add(flowNodeInstanceUpdate);\n"
        + "  } else if (flowNodeInstanceUpdate.mappedAs.equals(\"END\")\n"
        + "    && (wasAlreadyCorrelated ||  eventDate.after(dateFormatter.parse(flowNodeInstanceToUpdate.startDate)))) {\n"
        + "    flowNodeInstanceToUpdate.endDate = flowNodeInstanceUpdate.date;\n"
        + "    appliedUpdates.add(flowNodeInstanceUpdate);\n"
        + "  }\n"
        + "  if (appliedUpdates.contains(flowNodeInstanceUpdate)) {\n"
        + "    if (!correlatedAsInstanceIds.contains(flowNodeInstanceToUpdate.flowNodeInstanceId)) {\n"
        + "      correlatedAsInstanceIds.add(flowNodeInstanceToUpdate.flowNodeInstanceId);\n"
        + "    }\n"
        + "    calculateAndAssignEventDuration(flowNodeInstanceToUpdate, dateFormatter);\n"
        + "  }\n"
        + "}\n"
        + "}\n"
        + "processInstance.flowNodeInstances = new ArrayList(\n"
        + "existingFlowNodeInstancesByFlowNodeId.values().stream().flatMap(List::stream).collect(Collectors.toList())\n"
        + ");\n"
        + "processInstance.pendingFlowNodeInstanceUpdates = pendingFlowNodeInstanceUpdates.stream()\n"
        + ".filter(flowNodeInstanceUpdate -> !appliedUpdates.contains(flowNodeInstanceUpdate))\n"
        + ".collect(Collectors.toList());\n"
        + "addGatewaysForProcessInstance(processInstance, params.gatewayLookup, eventDateComparator, processInstanceUpdate.processInstanceId, dateFormatter);\n"
        + "updateProcessInstance(processInstance, dateFormatter);\n";
    // @formatter:on
  }

  private static String createInstanceUpdateFunction() {
    // @formatter:off
    return "void updateProcessInstance(def instance, def formatter) {\n"
        + "def startDate = instance.flowNodeInstances.stream()\n"
        + "  .filter(event -> event.flowNodeType.equals(\"startEvent\"))\n"
        + "  .map(event -> event.startDate)\n"
        + "  .filter(value -> value != null)\n"
        + "  .sorted()\n"
        + "  .findFirst()\n"
        + "  .ifPresent(value -> instance.startDate = value);\n"
        + "def endDate = instance.flowNodeInstances.stream()\n"
        + "  .filter(event -> event.flowNodeType.equals(\"endEvent\"))\n"
        + "  .map(event -> event.endDate)\n"
        + "  .filter(value -> value != null)\n"
        + "  .sorted(Comparator.reverseOrder())\n"
        + "  .findFirst()\n"
        + "  .ifPresent(value -> instance.endDate = value);\n"
        + "if(instance.endDate != null) {\n"
        + "  instance.state = \"COMPLETED\";\n"
        + "} else {"
        + "  instance.state = \"ACTIVE\";\n"
        + "}\n"
        + "if (instance.startDate != null && instance.endDate != null) {\n"
        + "  instance.duration = formatter.parse(instance.endDate).getTime() - formatter.parse(instance.startDate).getTime();\n"
        + "}"
        + "}\n";
    // @formatter:on
  }

  private static String createRemoveExistingGatewaysFunction() {
    // @formatter:off
    return "void removeExistingGateways(def instance, def gatewayLookup) {\n"
        + "gatewayLookup.forEach(gatewayEvent -> \n"
        + "instance.flowNodeInstances.removeIf(event -> gatewayEvent.id.equals(event.flowNodeId))\n"
        + ")"
        + "}\n";
    // @formatter:on
  }

  private static String createAddGatewaysForProcessInstanceFunction() {
    // @formatter:off
    return "void addGatewaysForProcessInstance(def instance, def gatewayLookup, def startEndDateComparator, def processInstanceId, def dateFormatter) {\n"
        + "List gatewayEventsToAdd = new ArrayList();\n"
        + "List gatewayIdsInModel = gatewayLookup.stream().map(gateway -> gateway.id).collect(Collectors.toList());\n"
        + "List existingEvents = new ArrayList(instance.flowNodeInstances);\n"
        + "if (!existingEvents.isEmpty() && !gatewayLookup.isEmpty()) { \n"
        + "Collections.sort(existingEvents, startEndDateComparator);\n"
        + "for (def possibleGateway : gatewayLookup) {\n"
        + "int eventAddedCount = 0;\n"
        +
        // Gateways with a single source flow node are opening gateways.
        "if (possibleGateway.previousNodeIds.size() == 1) {\n"
        + "addOpeningGatewayInstances(possibleGateway, gatewayIdsInModel, existingEvents, gatewayEventsToAdd,"
        + "eventAddedCount, processInstanceId, instance.processDefinitionKey, dateFormatter);\n"
        +
        // Gateways with a single target flow node are closing gateways.
        "} else if (possibleGateway.nextNodeIds.size() == 1) {\n"
        + "addClosingGatewayInstances(possibleGateway, gatewayIdsInModel, existingEvents, gatewayEventsToAdd,"
        + "eventAddedCount, processInstanceId, instance.processDefinitionKey, dateFormatter);\n"
        + "}\n"
        + "}\n"
        + "existingEvents.addAll(gatewayEventsToAdd);\n"
        + "Collections.sort(existingEvents, startEndDateComparator);\n"
        + "instance.flowNodeInstances = existingEvents;\n"
        + "}\n"
        + "}\n";
  }

  private static String addOpeningGatewayInstancesFunction() {
    // @formatter:off
    return "void addOpeningGatewayInstances(def possibleGateway, def gatewayIdsInModel, def existingEvents, def gatewayEventsToAdd,"
        + "def eventAddedCount, def processInstanceId, def definitionKey, def dateFormatter) {\n"
        +
        // For event based gateways
        "if (possibleGateway.type.equals(\"eventBasedGateway\")) {\n"
        + "def previousNodeId = possibleGateway.previousNodeIds.get(0);\n"
        +
        // If the source flow node is also a gateway, we create a new gateway for each occurrence of
        // one of the target flow
        //  node events, using the start date and end date of the target flow node event
        "if (gatewayIdsInModel.contains(previousNodeId)) {\n"
        + "for (def event : existingEvents) {\n"
        + "if (possibleGateway.nextNodeIds.contains(event.flowNodeId) && event.startDate != null) {\n"
        + "eventAddedCount ++;\n"
        + "def newGateway = createNewGateway(possibleGateway.id, possibleGateway.type, \n"
        + "event.startDate, event.startDate, processInstanceId, definitionKey, eventAddedCount);\n"
        + "gatewayEventsToAdd.add(newGateway);\n"
        + "}\n"
        + "}\n"
        +
        // If the source flow node is not a gateway, we create a new gateway for each occurrence of
        // one of the target flow node
        //  events, using the end date of the source flow node event and the start date of the
        // target flow node event
        "} else {\n"
        + "List targetEvents = existingEvents.stream()"
        + ".filter(event -> possibleGateway.nextNodeIds.contains(event.flowNodeId) && event.startDate != null)\n"
        + ".collect(Collectors.toList());\n"
        + "List sourceEvents = existingEvents.stream()"
        + ".filter(event -> possibleGateway.previousNodeIds.get(0).equals(event.flowNodeId) && event.endDate != null)\n"
        + ".collect(Collectors.toList());\n"
        + "boolean gatewayCanBeAdded = !sourceEvents.isEmpty() && !targetEvents.isEmpty();\n"
        + "while (gatewayCanBeAdded) {\n"
        + "def sourceEvent = sourceEvents.remove(0);\n"
        + "def targetEvent = targetEvents.remove(0);\n"
        + "eventAddedCount ++;\n"
        + "def newGateway = createNewGateway(possibleGateway.id, possibleGateway.type, \n"
        + "sourceEvent.endDate, targetEvent.startDate, processInstanceId, definitionKey, eventAddedCount);\n"
        + "calculateAndAssignEventDuration(newGateway, dateFormatter);\n"
        + "gatewayEventsToAdd.add(newGateway);\n"
        + "gatewayCanBeAdded = !sourceEvents.isEmpty() && !targetEvents.isEmpty();\n"
        + "}\n"
        + "}\n"
        +

        // For exclusive and parallel gateways
        // A gateway with a single source flow node that isn't a gateway can be added for every
        // occurrence of that flow node event
        "} else if (!gatewayIdsInModel.contains(possibleGateway.previousNodeIds.get(0))) {"
        + "for (def event : existingEvents) {\n"
        + "if (possibleGateway.previousNodeIds.contains(event.flowNodeId) && event.endDate != null) {\n"
        + "eventAddedCount ++;\n"
        + "def newGateway = createNewGateway(possibleGateway.id, possibleGateway.type, \n"
        + "event.endDate, event.endDate, processInstanceId, definitionKey, eventAddedCount);\n"
        + "gatewayEventsToAdd.add(newGateway);\n"
        + "}\n"
        + "}\n"
        +
        // A gateway with source flow node as a gateway can be added if the expected target flow
        // node event(s) have occurred
        "} else {\n"
        +
        // For any gateway type, this is when any one of its target flow node events have occurred
        "for (def event : existingEvents) {\n"
        + "if (possibleGateway.nextNodeIds.contains(event.flowNodeId)) {\n"
        + "eventAddedCount ++;\n"
        + "def newGateway = createNewGateway(possibleGateway.id, possibleGateway.type, \n"
        + "event.startDate, event.startDate, processInstanceId, definitionKey, eventAddedCount);\n"
        + "gatewayEventsToAdd.add(newGateway);\n"
        + "}\n"
        + "}\n"
        + "}\n"
        + "}\n";
    // @formatter:on
  }

  private static String addClosingGatewayInstancesFunction() {
    // @formatter:off
    return "void addClosingGatewayInstances(def possibleGateway, def gatewayIdsInModel, def existingEvents, def gatewayEventsToAdd,"
        + "def eventAddedCount, def processInstanceId, def definitionKey, def dateFormatter) {\n"
        +
        // For exclusive gateways
        "if (possibleGateway.type.equals(\"exclusiveGateway\")) {\n"
        +
        // if the target flow node event is not a gateway, we can add an exclusive gateway for every
        //  occurrence of the target flow node event
        "if (!gatewayIdsInModel.contains(possibleGateway.nextNodeIds.get(0))) {\n"
        + "for (def event : existingEvents) {\n"
        + "if (possibleGateway.nextNodeIds.contains(event.flowNodeId)) {\n"
        + "eventAddedCount ++;\n"
        + "def newGateway = createNewGateway(possibleGateway.id, possibleGateway.type, \n"
        + "event.startDate, event.startDate, processInstanceId, definitionKey, eventAddedCount);\n"
        + "gatewayEventsToAdd.add(newGateway);\n"
        + "}\n"
        + "}\n"
        +
        // if the target flow node event is a gateway, we can add an exclusive gateway for every
        // occurrence
        //  of one of the source flow node events
        "} else {"
        + "for (def event : existingEvents) {\n"
        + "if (possibleGateway.previousNodeIds.contains(event.flowNodeId) && event.endDate != null) {\n"
        + "eventAddedCount ++;\n"
        + "def newGateway = createNewGateway(possibleGateway.id, possibleGateway.type, \n"
        + "event.endDate, event.endDate, processInstanceId, definitionKey, eventAddedCount);\n"
        + "gatewayEventsToAdd.add(newGateway);\n"
        + "}\n"
        + "}\n"
        + "}\n"
        +

        // For parallel gateways
        "} else if (possibleGateway.type.equals(\"parallelGateway\")) {\n"
        + "Map relatedEventsByFlowNodeId = existingEvents.stream()\n"
        + ".filter(event -> possibleGateway.previousNodeIds.contains(event.flowNodeId) && event.endDate != null)\n"
        + ".collect(Collectors.groupingBy(event -> event.flowNodeId));\n"
        + "List mappablePreviousNodeIds = possibleGateway.previousNodeIds.stream()"
        + ".filter(nodeId -> !gatewayIdsInModel.contains(nodeId))\n"
        + ".collect(Collectors.toList());\n"
        +
        // We add a parallel gateway for every occurrence of all mappable source flow node events
        "boolean gatewayCanBeAdded = relatedEventsByFlowNodeId.keySet().containsAll(mappablePreviousNodeIds);\n"
        + "while (gatewayCanBeAdded) {\n"
        + "List eventsForGateway = new ArrayList();\n"
        + "for (def previousNodeId : mappablePreviousNodeIds) {\n"
        + "eventsForGateway.add(relatedEventsByFlowNodeId.get(previousNodeId).remove(0));\n"
        + "}\n"
        + "def endDateComparator = Comparator.comparing(event -> event.endDate, Comparator.nullsLast(Comparator.naturalOrder()));\n"
        + "Collections.sort(eventsForGateway, endDateComparator);\n"
        + "def firstEventForGateway = eventsForGateway.get(0);"
        + "def lastEventForGateway = eventsForGateway.get(eventsForGateway.size() - 1);"
        + "eventAddedCount ++;\n"
        + "def newGateway = createNewGateway(possibleGateway.id, possibleGateway.type, \n"
        + "firstEventForGateway.endDate, lastEventForGateway.endDate, processInstanceId, definitionKey, eventAddedCount);\n"
        + "calculateAndAssignEventDuration(newGateway, dateFormatter);\n"
        + "gatewayEventsToAdd.add(newGateway);\n"
        + "gatewayCanBeAdded = !relatedEventsByFlowNodeId.values().stream().anyMatch(eventsForActivity -> eventsForActivity.isEmpty());\n"
        + "}\n"
        + "}\n"
        + "}\n";
    // @formatter:on
  }

  private static String createNewGatewayFunction() {
    // @formatter:off
    return "def createNewGateway(def flowNodeId, def flowNodeType, def startDate, def endDate, def definitionKey, def processInstanceId, def eventAddedCount) {\n"
        + "def newGateway = [\n"
        + "'flowNodeInstanceId': flowNodeId + '_' + eventAddedCount,\n"
        + "'flowNodeId': flowNodeId,\n"
        + "'flowNodeType': flowNodeType,\n"
        + "'totalDurationInMs': 0,\n"
        + "'startDate': startDate,\n"
        + "'endDate': endDate,\n"
        + "'processInstanceId': processInstanceId,\n"
        + "'definitionKey': definitionKey,\n"
        + "'definitionVersion': \"1\"\n"
        + "];\n"
        + "return newGateway;\n"
        + "}\n";
    // @formatter:on
  }
}
