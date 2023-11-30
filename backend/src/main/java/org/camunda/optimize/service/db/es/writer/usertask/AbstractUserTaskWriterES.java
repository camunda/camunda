/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.writer.usertask;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.writer.usertask.AbstractUserTaskWriter;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.db.es.writer.AbstractProcessInstanceDataWriterES;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

@Conditional(ElasticSearchCondition.class)
@Slf4j
public abstract class AbstractUserTaskWriterES extends AbstractProcessInstanceDataWriterES<FlowNodeInstanceDto>
  implements AbstractUserTaskWriter {

  protected final ObjectMapper objectMapper;

  protected AbstractUserTaskWriterES(final OptimizeElasticsearchClient esClient,
                                     final ElasticSearchSchemaManager elasticSearchSchemaManager,
                                     final ObjectMapper objectMapper) {
    super(esClient, elasticSearchSchemaManager);
    this.objectMapper = objectMapper;
  }

  protected abstract String createInlineUpdateScript();

  protected UpdateRequest createUserTaskUpdateImportRequest(final Map.Entry<String, List<FlowNodeInstanceDto>> userTaskInstanceEntry) {
    final List<FlowNodeInstanceDto> userTasks = userTaskInstanceEntry.getValue();
    final String processInstanceId = userTaskInstanceEntry.getKey();

    final Script updateScript = createUpdateScript(userTasks);

    final FlowNodeInstanceDto firstUserTaskInstance = userTasks.stream().findFirst()
      .orElseThrow(() -> new OptimizeRuntimeException("No user tasks to import provided"));
    final ProcessInstanceDto procInst = ProcessInstanceDto.builder()
      .processInstanceId(processInstanceId)
      .dataSource(new EngineDataSourceDto(firstUserTaskInstance.getEngine()))
      .flowNodeInstances(userTasks)
      .build();
    String newEntryIfAbsent;
    try {
      newEntryIfAbsent = objectMapper.writeValueAsString(procInst);
    } catch (JsonProcessingException e) {
      String reason = String.format(
        "Error while processing JSON for user tasks of process instance with ID [%s].",
        processInstanceId
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return new UpdateRequest()
      .index(getProcessInstanceIndexAliasName(firstUserTaskInstance.getDefinitionKey()))
      .id(processInstanceId)
      .script(updateScript)
      .upsert(newEntryIfAbsent, XContentType.JSON)
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
  }

  @Override
  public List<ImportRequestDto> generateUserTaskImports(final String importItemName,
                                                        final DatabaseClient databaseClient,
                                                        final List<FlowNodeInstanceDto> userTaskInstances) {
    log.debug("Writing [{}] {} to ES.", userTaskInstances.size(), importItemName);

    createInstanceIndicesIfMissing(userTaskInstances, FlowNodeInstanceDto::getDefinitionKey);

    Map<String, List<FlowNodeInstanceDto>> userTaskToProcessInstance = new HashMap<>();
    for (FlowNodeInstanceDto userTask : userTaskInstances) {
      userTaskToProcessInstance.putIfAbsent(userTask.getProcessInstanceId(), new ArrayList<>());
      userTaskToProcessInstance.get(userTask.getProcessInstanceId()).add(userTask);
    }

    return userTaskToProcessInstance.entrySet().stream()
      .map(entry -> ImportRequestDto.builder()
        .importName(importItemName)
        .client(esClient)
        .request(createUserTaskUpdateImportRequest(entry))
        .build())
      .collect(Collectors.toList());
  }

  private Script createUpdateScript(List<FlowNodeInstanceDto> userTasks) {
    final ImmutableMap<String, Object> scriptParameters = ImmutableMap.of(FLOW_NODE_INSTANCES, userTasks);
    return createDefaultScriptWithSpecificDtoParams(createInlineUpdateScript(), scriptParameters, objectMapper);
  }

}
