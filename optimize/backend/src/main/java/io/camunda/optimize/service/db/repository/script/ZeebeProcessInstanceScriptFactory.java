/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.script;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import io.camunda.optimize.service.db.es.writer.usertask.UserTaskDurationScriptUtil;
import java.util.Set;
import java.util.stream.Collectors;

public interface ZeebeProcessInstanceScriptFactory {
  static String createProcessInstanceUpdateScript() {
    return createUpdateProcessInstancePropertiesScript()
        + createUpdateFlowNodeInstancesScript()
        + createUpdateIncidentsScript();
  }

  private static String createUpdateProcessInstancePropertiesScript() {
    final String simplePropertyUpdateScript =
        createUpdatePropertyIfNotNullScript(
            "newInstance",
            "existingInstance",
            Set.of(
                ProcessInstanceDto.Fields.processInstanceId,
                ProcessInstanceDto.Fields.processDefinitionKey,
                ProcessInstanceDto.Fields.processDefinitionVersion,
                ProcessInstanceDto.Fields.processDefinitionId,
                ProcessInstanceDto.Fields.startDate,
                ProcessInstanceDto.Fields.endDate,
                ProcessInstanceDto.Fields.state,
                ProcessInstanceDto.Fields.dataSource));
    return """
      def newInstance = params.instance;
      def existingInstance = ctx._source;
      """
        + simplePropertyUpdateScript
        + """
          if (existingInstance.startDate != null && existingInstance.endDate != null) {
            def dateFormatter = new SimpleDateFormat(params.dateFormatPattern);
            existingInstance.duration = dateFormatter.parse(existingInstance.endDate).getTime() -
              dateFormatter.parse(existingInstance.startDate).getTime();
          }
          if (existingInstance.variables == null) {
            existingInstance.variables = new ArrayList();
          }
          if (newInstance.variables != null) {
             existingInstance.variables = Stream.concat(existingInstance.variables.stream(), newInstance.variables.stream())
             .collect(Collectors.toMap(variable -> variable.id, Function.identity(), (oldVar, newVar) ->
                (newVar.version > oldVar.version) ? newVar : oldVar
             )).values();
          }
          """;
  }

  private static String createUpdateFlowNodeInstancesScript() {
    return """
      def flowNodesById = existingInstance.flowNodeInstances.stream()
        .collect(Collectors.toMap(flowNode -> flowNode.flowNodeInstanceId, flowNode -> flowNode, (f1, f2) -> f1));
      def newFlowNodes = params.instance.flowNodeInstances;
      """
        +
        // userTask import is allowed to overwrite flownode import values
        """
          def isUserTaskImport = "user-task".equals(params.sourceExportIndex);
          for (def newFlowNode : newFlowNodes) {
            def existingFlowNode = flowNodesById.get(newFlowNode.flowNodeInstanceId);
            if (existingFlowNode != null) {
              if (newFlowNode.endDate != null && (existingFlowNode.endDate == null || isUserTaskImport)) {
                existingFlowNode.endDate = newFlowNode.endDate;
              }
              if (newFlowNode.startDate != null && (existingFlowNode.startDate == null || isUserTaskImport)) {
                existingFlowNode.startDate = newFlowNode.startDate;
              }
              if (existingFlowNode.startDate != null && existingFlowNode.endDate != null) {
                def dateFormatter = new SimpleDateFormat(params.dateFormatPattern);
                existingFlowNode.totalDurationInMs = dateFormatter.parse(existingFlowNode.endDate).getTime() -
                  dateFormatter.parse(existingFlowNode.startDate).getTime();
              }
              if (newFlowNode.canceled != null) {
                existingFlowNode.canceled = newFlowNode.canceled;
              }
              if (existingFlowNode.assigneeOperations == null) {
                existingFlowNode.assigneeOperations = new ArrayList();
              }
              if (newFlowNode.assigneeOperations != null && !newFlowNode.assigneeOperations.isEmpty()) {
                def newAssigneeOperations = newFlowNode.assigneeOperations.stream()
                  .filter(Objects::nonNull)
                  .filter(newOperation -> !existingFlowNode.assigneeOperations.contains(newOperation))
                  .collect(Collectors.toList());
                existingFlowNode.assigneeOperations.addAll(newAssigneeOperations);
              }
              if (isUserTaskImport) {
                existingFlowNode.assignee = newFlowNode.assignee;
                existingFlowNode.candidateGroups = newFlowNode.candidateGroups;
              }
            } else {
              flowNodesById.put(newFlowNode.flowNodeInstanceId, newFlowNode);
            }
          }
          existingInstance.flowNodeInstances = flowNodesById.values();
          """
        + UserTaskDurationScriptUtil.createUpdateUserTaskMetricsScript();
  }

  private static String createUpdateIncidentsScript() {
    final String simplePropertyUpdateScript =
        createUpdatePropertyIfNotNullScript(
            "newIncident",
            "existingIncident",
            Set.of(IncidentDto.Fields.createTime, IncidentDto.Fields.endTime));
    return """
      def incidentsById = existingInstance.incidents.stream()
        .collect(Collectors.toMap(incident -> incident.id, incident -> incident, (f1, f2) -> f1));
      def newIncidents = params.instance.incidents;
      for (def newIncident : newIncidents) {
        def existingIncident = incidentsById.get(newIncident.id);
        if (existingIncident != null) {
      """
        + simplePropertyUpdateScript
        + """
              if (existingIncident.createTime != null && existingIncident.endTime != null) {
                def dateFormatter = new SimpleDateFormat(params.dateFormatPattern);
                existingIncident.durationInMs = dateFormatter.parse(existingIncident.endTime).getTime() -
                  dateFormatter.parse(existingIncident.createTime).getTime();
              }
              if (existingIncident.incidentStatus.equals("open")) {
                existingIncident.incidentStatus = newIncident.incidentStatus;
              }
            } else {
              incidentsById.put(newIncident.id, newIncident);
            }
          }
          """
        +
        // We have to set the correct properties for incidents that we can't get from the record
        """
              def flowNodeIdsByFlowNodeInstanceIds = flowNodesById.values()
                .stream()
                .collect(Collectors.toMap(flowNode -> flowNode.flowNodeInstanceId, flowNode -> flowNode.flowNodeId));
              existingInstance.incidents = incidentsById.values()
                .stream()
                .peek(incident -> {
                   def flowNodeId = flowNodeIdsByFlowNodeInstanceIds.get(incident.activityId);
                   if (flowNodeId != null) {
                     incident.activityId = flowNodeId;
                   }
                   incident.definitionVersion = existingInstance.processDefinitionVersion;
                   return incident;
                })
                .collect(Collectors.toList());
          """;
  }

  private static String createUpdatePropertyIfNotNullScript(
      final String newEntityName,
      final String existingEntityName,
      final Set<String> propertiesToUpdate) {
    final String simplePropertyUpdateScript =
        """
      if (%s.%s != null) {
        %s.%s = %s.%s;
      }
      """;
    return propertiesToUpdate.stream()
        .map(
            propertyName ->
                simplePropertyUpdateScript.formatted(
                    newEntityName,
                    propertyName,
                    existingEntityName,
                    propertyName,
                    newEntityName,
                    propertyName))
        .collect(Collectors.joining("\n"));
  }
}
