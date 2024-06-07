/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.os;

import static org.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.DatabaseConstants.VARIABLE_LABEL_INDEX_NAME;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.script;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import org.camunda.optimize.service.db.DatabaseConstants;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchDocumentOperations;
import org.camunda.optimize.service.db.os.reader.OpensearchReaderUtil;
import org.camunda.optimize.service.db.os.schema.index.VariableUpdateInstanceIndexOS;
import org.camunda.optimize.service.db.repository.VariableRepository;
import org.camunda.optimize.service.db.repository.script.ProcessInstanceScriptFactory;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.db.schema.ScriptData;
import org.camunda.optimize.service.db.schema.index.VariableUpdateInstanceIndex;
import org.camunda.optimize.service.db.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.MgetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchRequest.Builder;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.opensearch.client.opensearch.core.get.GetResult;
import org.opensearch.client.opensearch.core.mget.MultiGetResponseItem;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
@Conditional(OpenSearchCondition.class)
public class VariableRepositoryOS implements VariableRepository {
  private final OptimizeOpenSearchClient osClient;
  private final OptimizeIndexNameService indexNameService;
  private final ConfigurationService configurationService;

  @Override
  public void deleteVariableDataByProcessInstanceIds(
      final String processDefinitionKey, final List<String> processInstanceIds) {
    final List<BulkOperation> bulkOperations =
        processInstanceIds.stream()
            .map(
                processInstanceId -> {
                  final UpdateOperation<DecisionDefinitionOptimizeDto> updateOperation =
                      new UpdateOperation.Builder<DecisionDefinitionOptimizeDto>()
                          .index(
                              indexNameService.getOptimizeIndexAliasForIndex(
                                  getProcessInstanceIndexAliasName(processDefinitionKey)))
                          .id(processInstanceId)
                          .script(
                              script(
                                  ProcessInstanceScriptFactory.createVariableClearScript(),
                                  Map.of()))
                          .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                          .build();
                  return new BulkOperation.Builder().update(updateOperation).build();
                })
            .toList();

    osClient.doBulkRequest(
        () -> new BulkRequest.Builder().refresh(Refresh.True),
        bulkOperations,
        getProcessInstanceIndexAliasName(processDefinitionKey),
        false);
  }

  @Override
  public void upsertVariableLabel(
      final String variableLabelIndexName,
      final DefinitionVariableLabelsDto definitionVariableLabelsDto,
      final ScriptData scriptData) {

    final UpdateRequest.Builder updateRequest =
        new UpdateRequest.Builder()
            .index(variableLabelIndexName)
            .id(definitionVariableLabelsDto.getDefinitionKey().toLowerCase(Locale.ENGLISH))
            .scriptedUpsert(true)
            .script(QueryDSL.script(scriptData.scriptString(), scriptData.params()))
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
            .refresh(Refresh.True)
            .upsert(definitionVariableLabelsDto);

    osClient
        .getRichOpenSearchClient()
        .doc()
        .upsert(
            updateRequest,
            DefinitionVariableLabelsDto.class,
            e ->
                String.format(
                    "Was not able to update the variable labels for the process definition with id: [%s]",
                    definitionVariableLabelsDto.getDefinitionKey()));
  }

  @Override
  public void deleteVariablesForDefinition(
      final String variableLabelIndexName, final String processDefinitionKey) {
    osClient.delete(variableLabelIndexName, processDefinitionKey);
  }

  @Override
  public Map<String, DefinitionVariableLabelsDto> getVariableLabelsByKey(
      final List<String> processDefinitionKeys) {
    final Map<String, String> map = new HashMap<>();
    processDefinitionKeys.forEach(
        processDefinitionKey ->
            map.put(VARIABLE_LABEL_INDEX_NAME, processDefinitionKey.toLowerCase(Locale.ENGLISH)));

    final String errorMessage =
        String.format(
            "There was an error while fetching documents from the variable label index with keys %s.",
            processDefinitionKeys);
    final MgetResponse<DefinitionVariableLabelsDto> response =
        osClient.mget(DefinitionVariableLabelsDto.class, errorMessage, map);

    return response.docs().stream()
        .map(MultiGetResponseItem::result)
        .filter(GetResult::found)
        .map(GetResult::source)
        .filter(Objects::nonNull)
        .peek(label -> label.setDefinitionKey(label.getDefinitionKey().toLowerCase(Locale.ENGLISH)))
        .collect(
            Collectors.toMap(DefinitionVariableLabelsDto::getDefinitionKey, Function.identity()));
  }

  @Override
  public void deleteByProcessInstanceIds(final List<String> processInstanceIds) {
    osClient.deleteByQueryTask(
        String.format("variable updates of %d process instances", processInstanceIds.size()),
        QueryDSL.stringTerms(VariableUpdateInstanceIndex.PROCESS_INSTANCE_ID, processInstanceIds),
        false,
        osClient
            .getIndexNameService()
            .getOptimizeIndexNameWithVersionWithWildcardSuffix(
                new VariableUpdateInstanceIndexOS()));
  }

  @Override
  public List<VariableUpdateInstanceDto> getVariableInstanceUpdatesForProcessInstanceIds(
      final Set<String> processInstanceIds) {

    final Query query =
        QueryDSL.stringTerms(VariableUpdateInstanceIndex.PROCESS_INSTANCE_ID, processInstanceIds);
    SearchRequest.Builder searchRequest =
        new Builder()
            .index(DatabaseConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME)
            .query(query)
            .sort(
                new SortOptions.Builder()
                    .field(
                        new FieldSort.Builder()
                            .field(CamundaActivityEventIndex.TIMESTAMP)
                            .order(SortOrder.Asc)
                            .build())
                    .build())
            .size(MAX_RESPONSE_SIZE_LIMIT)
            .scroll(
                new Time.Builder()
                    .time(
                        String.valueOf(
                            configurationService
                                .getOpenSearchConfiguration()
                                .getScrollTimeoutInSeconds()))
                    .build());
    final OpenSearchDocumentOperations.AggregatedResult<Hit<VariableUpdateInstanceDto>> scrollResp;
    try {
      scrollResp =
          osClient.retrieveAllScrollResults(searchRequest, VariableUpdateInstanceDto.class);
    } catch (final IOException e) {
      log.error("Was not able to retrieve private entities!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve private entities!", e);
    }

    return OpensearchReaderUtil.extractAggregatedResponseValues(scrollResp);
  }
}
