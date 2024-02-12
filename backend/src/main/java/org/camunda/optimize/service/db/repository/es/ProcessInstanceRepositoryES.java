/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.es;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.db.repository.ProcessInstanceRepository;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

import static java.lang.String.format;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.ACTIVE_STATE;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
class ProcessInstanceRepositoryES implements ProcessInstanceRepository {
  private final ConfigurationService configurationService;
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  @Override
  public void updateProcessInstanceStateForProcessDefinitionId(
    final String importItemName,
    final String definitionKey,
    final String processDefinitionId,
    final String state
  ) {
    ElasticsearchWriterUtil.tryUpdateByQueryRequest(
      esClient,
      format("%s with %s: %s", importItemName, ProcessInstanceDto.Fields.processDefinitionId, processDefinitionId),
      createUpdateStateScript(state),
      termsQuery(ProcessInstanceDto.Fields.processDefinitionId, processDefinitionId),
      getProcessInstanceIndexAliasName(definitionKey)
    );
  }

  @Override
  public void updateAllProcessInstancesStates(
    final String importItemName,
    final String definitionKey,
    final String state
  ) {
    ElasticsearchWriterUtil.tryUpdateByQueryRequest(
      esClient,
      format("%s with %s: %s", importItemName, ProcessInstanceDto.Fields.processDefinitionKey, definitionKey),
      createUpdateStateScript(state),
      matchAllQuery(),
      getProcessInstanceIndexAliasName(definitionKey)
    );
  }

  @Override
  public void doImportBulkRequestWithList(final String importItemName, final List<ProcessInstanceDto> processInstanceDtos) {
    esClient.doImportBulkRequestWithList(
      importItemName,
      processInstanceDtos,
      (request, dto) -> addImportProcessInstanceRequest(
        request,
        dto,
        createUpdateStateScript(dto.getState()),
        objectMapper
      ),
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }

  private void addImportProcessInstanceRequest(BulkRequest bulkRequest,
                                                 ProcessInstanceDto processInstanceDto,
                                                 Script updateScript,
                                                 ObjectMapper objectMapper) {
    final UpdateRequest updateRequest = createUpdateRequestDto(processInstanceDto, updateScript, objectMapper);
    bulkRequest.add(updateRequest);
  }

  private UpdateRequest createUpdateRequestDto(final ProcessInstanceDto processInstanceDto,
                                               final Script updateScript,
                                               final ObjectMapper objectMapper) {
    String newEntryIfAbsent;
    try {
      newEntryIfAbsent = objectMapper.writeValueAsString(processInstanceDto);
    } catch (JsonProcessingException e) {
      String reason =
        String.format(
          "Error while processing JSON for process instance DTO with ID [%s].",
          processInstanceDto.getProcessInstanceId()
        );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return new UpdateRequest()
      .index(getProcessInstanceIndexAliasName(processInstanceDto.getProcessDefinitionKey()))
      .id(processInstanceDto.getProcessInstanceId())
      .script(updateScript)
      .upsert(newEntryIfAbsent, XContentType.JSON)
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
  }

  private Script createUpdateStateScript(final String newState) {
    final ImmutableMap<String, Object> scriptParameters = createUpdateStateScriptParamsMap(newState);
    return ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams(createInlineUpdateScript(), scriptParameters);
  }

  private String createInlineUpdateScript() {
    // @formatter:off
    return
      "if ((ctx._source.state == params.activeState || ctx._source.state == params.suspendedState) " +
        "&& (params.newState.equals(params.activeState) || params.newState.equals(params.suspendedState))) {" +
        "ctx._source.state = params.newState;" +
        "}\n"
      ;
    // @formatter:on
  }

  private ImmutableMap<String, Object> createUpdateStateScriptParamsMap(final String newState) {
    return ImmutableMap.of(
      "activeState", ACTIVE_STATE,
      "suspendedState", SUSPENDED_STATE,
      "newState", newState
    );
  }
}
