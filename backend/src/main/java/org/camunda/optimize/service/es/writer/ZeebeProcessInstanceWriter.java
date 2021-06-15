/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@Component
@Slf4j
public class ZeebeProcessInstanceWriter extends AbstractProcessInstanceDataWriter<ProcessInstanceDto> {

  private static final String NEW_INSTANCE = "instance";
  private static final String FORMATTER = "dateFormatPattern";

  private final ObjectMapper objectMapper;

  public ZeebeProcessInstanceWriter(final OptimizeElasticsearchClient esClient,
                                    final ElasticSearchSchemaManager elasticSearchSchemaManager,
                                    final ObjectMapper objectMapper) {
    super(esClient, elasticSearchSchemaManager);
    this.objectMapper = objectMapper;
  }

  public List<ImportRequestDto> generateProcessInstanceImports(List<ProcessInstanceDto> processInstances) {
    String importItemName = "zeebe process instances";
    log.debug("Creating imports for {} [{}].", processInstances.size(), importItemName);

    createInstanceIndicesIfMissing(processInstances, ProcessInstanceDto::getProcessDefinitionKey);

    return processInstances.stream()
      .map(procInst -> {
             final Map<String, Object> params = new HashMap<>();
             params.put(NEW_INSTANCE, procInst);
             params.put(FORMATTER, OPTIMIZE_DATE_FORMAT);
             try {
               final Script updateScript = createDefaultScriptWithSpecificDtoParams(
                 createProcessInstanceUpdateScript(),
                 params,
                 objectMapper
               );
               String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);
               return ImportRequestDto.builder()
                 .importName(importItemName)
                 .esClient(esClient)
                 .request(new UpdateRequest()
                            .index(getProcessInstanceIndexAliasName(procInst.getProcessDefinitionKey()))
                            .id(procInst.getProcessInstanceId())
                            .script(updateScript)
                            .upsert(newEntryIfAbsent, XContentType.JSON)
                            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT))
                 .build();
             } catch (IOException e) {
               String reason = String.format(
                 "Error while processing JSON for zeebe process instance with ID [%s].",
                 procInst.getProcessInstanceId()
               );
               throw new OptimizeRuntimeException(reason, e);
             }
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

      // Update the flow node instances
      "def flowNodesById = existingInstance.flowNodeInstances.stream()\n" +
        ".collect(Collectors.toMap(flowNode -> flowNode.flowNodeInstanceId, flowNode -> flowNode, (f1, f2) -> f1));\n" +
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
      "existingInstance.flowNodeInstances = flowNodesById.values();\n";
    // @formatter:on
  }

}