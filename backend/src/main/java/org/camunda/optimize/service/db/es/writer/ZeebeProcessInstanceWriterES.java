/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.RequestType;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.db.writer.DatabaseWriterUtil;
import org.camunda.optimize.service.db.writer.ZeebeProcessInstanceWriter;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class ZeebeProcessInstanceWriterES extends AbstractProcessInstanceDataWriterES<ProcessInstanceDto> implements ZeebeProcessInstanceWriter {

  private final ObjectMapper objectMapper;

  public ZeebeProcessInstanceWriterES(final OptimizeElasticsearchClient esClient,
                                      final ElasticSearchSchemaManager elasticSearchSchemaManager,
                                      final ObjectMapper objectMapper) {
    super(esClient, elasticSearchSchemaManager);
    this.objectMapper = objectMapper;
  }

  @Override
  public List<ImportRequestDto> generateProcessInstanceImports(List<ProcessInstanceDto> processInstances) {
    String importItemName = "zeebe process instances";
    log.debug("Creating imports for {} [{}].", processInstances.size(), importItemName);

    createInstanceIndicesIfMissing(processInstances, ProcessInstanceDto::getProcessDefinitionKey);

    return processInstances.stream()
      .map(procInst -> {
             final Map<String, Object> params = new HashMap<>();
             params.put(NEW_INSTANCE, procInst);
             params.put(FORMATTER, OPTIMIZE_DATE_FORMAT);
             return ImportRequestDto.builder()
               .importName(importItemName)
               .type(RequestType.UPDATE)
               .id(procInst.getProcessInstanceId())
               .indexName(getProcessInstanceIndexAliasName(procInst.getProcessDefinitionKey()))
               .source(procInst)
               .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
               .scriptData(DatabaseWriterUtil.createScriptData(createProcessInstanceUpdateScript(), params, objectMapper))
               .build();
           }
      )
      .collect(Collectors.toList());
  }

  private String createProcessInstanceUpdateScript() {
    // @formatter:off
    return
      // Update the instance
      "def newInstance = params.instance;\n" +
      "def existingInstance = ctx._source;\n" +
      "if (newInstance.processDefinitionKey != null) {\n" +
      "  existingInstance.processDefinitionKey = newInstance.processDefinitionKey;\n" +
      "}\n" +
      "if (newInstance.processDefinitionVersion != null) {\n" +
      "  existingInstance.processDefinitionVersion = newInstance.processDefinitionVersion;\n" +
      "}\n" +
      "if (newInstance.processDefinitionId != null) {\n" +
      "  existingInstance.processDefinitionId = newInstance.processDefinitionId;\n" +
      "}\n" +
      "if (newInstance.processInstanceId != null) {\n" +
      "  existingInstance.processInstanceId = newInstance.processInstanceId;\n" +
      "}\n" +
      "if (newInstance.endDate != null) {\n" +
      "  existingInstance.endDate = newInstance.endDate;\n" +
      "}\n" +
      "if (newInstance.startDate != null) {\n" +
      "  existingInstance.startDate = newInstance.startDate;\n" +
      "}\n" +
      "if (existingInstance.startDate != null && existingInstance.endDate != null) {\n" +
      "  def dateFormatter = new SimpleDateFormat(params.dateFormatPattern);\n" +
      "  existingInstance.duration = dateFormatter.parse(existingInstance.endDate).getTime() " +
      "    - dateFormatter.parse(existingInstance.startDate).getTime();\n" +
      "}\n" +
      "if (newInstance.state != null) {\n" +
      "  existingInstance.state = newInstance.state;\n" +
      "}\n" +
      "if (newInstance.dataSource != null) {\n" +
      "  existingInstance.dataSource = newInstance.dataSource;\n" +
      "}\n" +
      "if (existingInstance.variables == null) {\n" +
      "  existingInstance.variables = new ArrayList() \n" +
      "}\n" +
      "if (existingInstance.variables == null) {\n" +
      " existingInstance.variables = new ArrayList() \n" +
      "}\n" +
      "if (newInstance.variables != null) {\n" +
      "   existingInstance.variables = Stream.concat(existingInstance.variables.stream(), newInstance.variables.stream())\n" +
      "   .collect(Collectors.toMap(variable -> variable.id, Function.identity(), (oldVar, newVar) -> \n" +
      "      (newVar.version > oldVar.version) ? newVar : oldVar \n" +
      "   )).values();\n" +
      "}\n" +

      // Update the flow node instances
      "def flowNodesById = existingInstance.flowNodeInstances.stream()\n" +
      "  .collect(Collectors.toMap(flowNode -> flowNode.flowNodeInstanceId, flowNode -> flowNode, (f1, f2) -> f1));\n" +
      "def newFlowNodes = params.instance.flowNodeInstances;\n" +
      "for (def newFlowNode : newFlowNodes) {\n" +
      "  def existingFlowNode = flowNodesById.get(newFlowNode.flowNodeInstanceId);\n" +
      "  if (existingFlowNode != null) {\n" +
      "    if (newFlowNode.endDate != null) {\n" +
      "      existingFlowNode.endDate = newFlowNode.endDate;\n" +
      "    }\n" +
      "    if (newFlowNode.startDate != null) {\n" +
      "      existingFlowNode.startDate = newFlowNode.startDate;\n" +
      "    }\n" +
      "    if (existingFlowNode.startDate != null && existingFlowNode.endDate != null) {\n" +
      "      def dateFormatter = new SimpleDateFormat(params.dateFormatPattern);\n" +
      "      existingFlowNode.totalDurationInMs = dateFormatter.parse(existingFlowNode.endDate).getTime() " +
      "        - dateFormatter.parse(existingFlowNode.startDate).getTime();\n" +
      "    }\n" +
      "    if (newFlowNode.canceled != null) {\n" +
      "      existingFlowNode.canceled = newFlowNode.canceled;\n" +
      "    }\n" +
      "  } else {\n" +
      "    flowNodesById.put(newFlowNode.flowNodeInstanceId, newFlowNode);\n" +
      "  }\n" +
      "}\n" +
      "existingInstance.flowNodeInstances = flowNodesById.values();\n" +

    // Update the incidents
    "def incidentsById = existingInstance.incidents.stream()\n" +
    "  .collect(Collectors.toMap(incident -> incident.id, incident -> incident, (f1, f2) -> f1));\n" +
    "def newIncidents = params.instance.incidents;\n" +
    "for (def newIncident : newIncidents) {\n" +
    "  def existingIncident = incidentsById.get(newIncident.id);\n" +
    "  if (existingIncident != null) {\n" +
    "    if (newIncident.endTime != null) {\n" +
    "      existingIncident.endTime = newIncident.endTime;\n" +
    "    }\n" +
    "    if (newIncident.createTime != null) {\n" +
    "      existingIncident.createTime = newIncident.createTime;\n" +
    "    }\n" +
    "    if (existingIncident.createTime != null && existingIncident.endTime != null) {\n" +
    "      def dateFormatter = new SimpleDateFormat(params.dateFormatPattern);\n" +
    "      existingIncident.durationInMs = dateFormatter.parse(existingIncident.endTime).getTime() " +
    "        - dateFormatter.parse(existingIncident.createTime).getTime();\n" +
    "    }\n" +
    "    if (existingIncident.incidentStatus.equals(\"open\")) {\n" +
    "      existingIncident.incidentStatus = newIncident.incidentStatus;\n" +
    "    }\n" +
    "  } else {\n" +
    "    incidentsById.put(newIncident.id, newIncident);\n" +
    "  }\n" +
    "}\n" +
    // We have to set the correct properties for incidents that we can't get from the record
    "def flowNodeIdsByFlowNodeInstanceIds = flowNodesById.values()\n" +
    "  .stream()\n" +
    "  .collect(Collectors.toMap(flowNode -> flowNode.flowNodeInstanceId, flowNode -> flowNode.flowNodeId));\n" +
    "existingInstance.incidents = incidentsById.values()\n" +
    "  .stream()\n" +
    "  .peek(incident -> {" +
    "     def flowNodeId = flowNodeIdsByFlowNodeInstanceIds.get(incident.activityId);\n" +
    "     if (flowNodeId != null) {\n" +
    "       incident.activityId = flowNodeId;\n" +
    "     }\n" +
    "     incident.definitionVersion = existingInstance.processDefinitionVersion;\n" +
    "     return incident;\n" +
    "  })\n" +
    "  .collect(Collectors.toList());\n";
    // @formatter:on
  }

}