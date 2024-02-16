/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.es;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.EventProcessGatewayDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.db.repository.ProcessInstanceRepository;
import org.camunda.optimize.service.db.repository.script.ProcessInstanceScriptFactory;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static java.lang.String.format;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.ACTIVE_STATE;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCE_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLE_ID;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
class ProcessInstanceRepositoryES implements ProcessInstanceRepository {
  private final ConfigurationService configurationService;
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final DateTimeFormatter dateTimeFormatter;

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
  public void deleteByIds(final String index, final String itemName, final List<String> processInstanceIds) {
    final BulkRequest bulkRequest = new BulkRequest();
    processInstanceIds.forEach(
      id -> bulkRequest.add(new DeleteRequest(index, id))
    );
    esClient.doBulkRequest(
      bulkRequest,
      index,
      false
    );
  }

  @Override
  public void bulkImport(final String bulkRequestName, final List<ImportRequestDto> importRequests) {
    esClient.executeImportRequestsAsBulk(
      bulkRequestName,
      importRequests,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }

  @Override
  public void bulkImportProcessInstances(final String importItemName, final List<ProcessInstanceDto> processInstanceDtos) {
    doImportBulkRequestWithList(
      importItemName,
      processInstanceDtos,
      (request, dto) -> addImportProcessInstanceRequest(
        getProcessInstanceIndexAliasName(dto.getProcessDefinitionKey()),
        request,
        dto,
        createUpdateStateScript(dto.getState()),
        objectMapper
      )
    );
  }

  @Override
  public void bulkImportEvents(
    final String index,
    final String importItemName,
    final List<EventProcessInstanceDto> processInstanceDtos,
    final List<EventProcessGatewayDto> gatewayLookup
  ) {
    final List<Map> gatewayLookupMaps = gatewayLookup.stream()
      .map(gateway -> objectMapper.convertValue(gateway, new TypeReference<Map>() {
      })).toList();
    final BiConsumer<BulkRequest, EventProcessInstanceDto> addDtoToRequestConsumer = (request, dto) -> {
      final Map<String, Object> params = Map.of(
      "processInstance", dto,
      "gatewayLookup", gatewayLookupMaps,
      "dateFormatPattern", OPTIMIZE_DATE_FORMAT
      );

      final Script script = createDefaultScriptWithSpecificDtoParams(
        ProcessInstanceScriptFactory.createEventInlineUpdateScript(),
        params,
        objectMapper
      );

      addImportProcessInstanceRequest(index, request, dto, script, objectMapper);
    };

    doImportBulkRequestWithList(importItemName, processInstanceDtos, addDtoToRequestConsumer);
  }

  @Override
  public void deleteEndedBefore(final String index, final OffsetDateTime endDate, final String deletedItemIdentifier) {
    final BoolQueryBuilder filterQuery = boolQuery().filter(rangeQuery(END_DATE).lt(dateTimeFormatter.format(endDate)));
    ElasticsearchWriterUtil.tryDeleteByQueryRequest(esClient, filterQuery, deletedItemIdentifier, false, index);
  }

  @Override
  public void deleteVariablesOfInstancesThatEndedBefore(final String index, final OffsetDateTime endDate, final String updateItem) {
    final BoolQueryBuilder filterQuery = boolQuery()
      .filter(rangeQuery(END_DATE).lt(dateTimeFormatter.format(endDate)))
      .filter(nestedQuery(VARIABLES, existsQuery(VARIABLES + "." + VARIABLE_ID), ScoreMode.None));
    final Script script = new Script(ProcessInstanceScriptFactory.createVariableClearScript());
    ElasticsearchWriterUtil.tryUpdateByQueryRequest(esClient, updateItem, script, filterQuery, index);
  }

  @Override
  public void deleteEventsWithIdsInFromAllInstances(
    final String index,
    final List<String> eventIdsToDelete,
    final String updateItem
  ) {
    final Map<String, Object> params = Map.of("eventIdsToDelete", eventIdsToDelete);
    final Script script = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      ProcessInstanceScriptFactory.createDeleteEventsWithIdsInScript(),
      params
    );
    final NestedQueryBuilder query = nestedQuery(
      FLOW_NODE_INSTANCES,
      termsQuery(FLOW_NODE_INSTANCES + "." + FLOW_NODE_INSTANCE_ID, eventIdsToDelete.toArray()),
      ScoreMode.None
    );
    ElasticsearchWriterUtil.tryUpdateByQueryRequest(esClient, updateItem, script, query, index);
  }

  private <T> void doImportBulkRequestWithList(
    final String importItemName,
    final List<T> processInstanceDtos,
    final BiConsumer<BulkRequest, T> addDtoToRequestConsumer
  ) {
    esClient.doImportBulkRequestWithList(
      importItemName,
      processInstanceDtos,
      addDtoToRequestConsumer,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }

  private void addImportProcessInstanceRequest(
    final String index,
    BulkRequest bulkRequest,
    ProcessInstanceDto processInstanceDto,
    Script updateScript,
    ObjectMapper objectMapper
  ) {
    bulkRequest.add(createUpdateRequestDto(index, processInstanceDto, updateScript, objectMapper));
  }

  private UpdateRequest createUpdateRequestDto(
    final String index,
    final ProcessInstanceDto processInstanceDto,
    final Script updateScript,
    final ObjectMapper objectMapper
  ) {
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
      .index(index)
      .id(processInstanceDto.getProcessInstanceId())
      .script(updateScript)
      .scriptedUpsert(true)
      .upsert(newEntryIfAbsent, XContentType.JSON)
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
  }

  private Script createUpdateStateScript(final String newState) {
    final ImmutableMap<String, Object> scriptParameters = createUpdateStateScriptParamsMap(newState);
    return ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams(
      ProcessInstanceScriptFactory.createInlineUpdateScript(),
      scriptParameters
    );
  }

  private ImmutableMap<String, Object> createUpdateStateScriptParamsMap(final String newState) {
    return ImmutableMap.of(
      "activeState", ACTIVE_STATE,
      "suspendedState", SUSPENDED_STATE,
      "newState", newState
    );
  }
}
