/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.os;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.EventProcessGatewayDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.repository.ProcessInstanceRepository;
import org.camunda.optimize.service.db.repository.script.ProcessInstanceScriptFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.lang.String.format;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.ACTIVE_STATE;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.and;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.exists;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.json;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.lt;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.matchAll;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.nested;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.script;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.stringTerms;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.term;
import static org.camunda.optimize.service.db.os.writer.OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCE_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLE_ID;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

@Component
@RequiredArgsConstructor
@Conditional(OpenSearchCondition.class)
class ProcessInstanceRepositoryOS implements ProcessInstanceRepository {
  private final ConfigurationService configurationService;
  private final OptimizeOpenSearchClient osClient;
  private final ObjectMapper objectMapper;
  private final DateTimeFormatter dateTimeFormatter;

  @Override
  public void updateProcessInstanceStateForProcessDefinitionId(
    final String importItemName,
    final String definitionKey,
    final String processDefinitionId,
    final String state
  ) {
    osClient.updateByQueryTask(
      format("%s with %s: %s", importItemName, ProcessInstanceDto.Fields.processDefinitionId, processDefinitionId),
      createUpdateStateScript(state),
      term(ProcessInstanceDto.Fields.processDefinitionId, processDefinitionId),
      getProcessInstanceIndexAliasName(definitionKey)
    );
  }

  @Override
  public void updateAllProcessInstancesStates(
    final String importItemName,
    final String definitionKey,
    final String state
  ) {
    osClient.updateByQueryTask(
      format("%s with %s: %s", importItemName, ProcessInstanceDto.Fields.processDefinitionKey, definitionKey),
      createUpdateStateScript(state),
      matchAll(),
      getProcessInstanceIndexAliasName(definitionKey)
    );
  }

  @Override
  public void bulkImportProcessInstances(final String importItemName, final List<ProcessInstanceDto> processInstanceDtos) {
    osClient.doImportBulkRequestWithList(
      importItemName,
      processInstanceDtos,
      dto -> UpdateOperation.<ProcessInstanceDto>of(
        operation -> operation.index(getProcessInstanceIndexAliasName(dto.getProcessDefinitionKey()))
          .id(dto.getProcessInstanceId())
          .script(createUpdateStateScript(dto.getState()))
          .upsert(dto)
          .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
      )._toBulkOperation(),
      configurationService.getSkipDataAfterNestedDocLimitReached(),
      importItemName
    );
  }

  @Override
  public void deleteByIds(final String index, String itemName, final List<String> processInstanceIds) {
    List<BulkOperation> bulkOperations = processInstanceIds.stream()
      .map(
        id -> BulkOperation.of(
          op -> op.delete(
            d -> d.index(index).id(id)
          )
        )
      )
      .toList();

    osClient.doBulkRequest(new BulkRequest.Builder(), bulkOperations, itemName, false);
  }

  @Override
  public void bulkImport(final String bulkRequestName, final List<ImportRequestDto> importRequests) {
    osClient.executeImportRequestsAsBulk(
      bulkRequestName,
      importRequests,
      configurationService.getSkipDataAfterNestedDocLimitReached()
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
    final Function<EventProcessInstanceDto, Script> scriptBuilder = dto -> {
      final Map<String, JsonData> params = Map.of(
        "processInstance", json(dto),
        "gatewayLookup", json(gatewayLookupMaps),
        "dateFormatPattern", json(OPTIMIZE_DATE_FORMAT)
      );
      return createDefaultScriptWithPrimitiveParams(ProcessInstanceScriptFactory.createEventInlineUpdateScript(), params);
    };

    osClient.doImportBulkRequestWithList(
      importItemName,
      processInstanceDtos,
      dto -> UpdateOperation.<ProcessInstanceDto>of(
        operation -> operation.index(index)
          .id(dto.getProcessInstanceId())
          .script(scriptBuilder.apply(dto))
          .upsert(dto)
          .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
      )._toBulkOperation(),
      configurationService.getSkipDataAfterNestedDocLimitReached(),
      importItemName
    );
  }

  @Override
  public void deleteEndedBefore(final String index, final OffsetDateTime endDate, final String deletedItemIdentifier) {
    osClient.deleteByQueryTask(
      deletedItemIdentifier,
      lt(END_DATE, dateTimeFormatter.format(endDate)),
      false,
      index
    );
  }

  @Override
  public void deleteVariablesOfInstancesThatEndedBefore(
    final String index,
    final OffsetDateTime endDate,
    final String updateItem
  ) {
    osClient.updateByQueryTask(
      updateItem,
      script(ProcessInstanceScriptFactory.createVariableClearScript(), Map.of()),
      and(
        lt(END_DATE, dateTimeFormatter.format(endDate)),
        nested(VARIABLES, exists(VARIABLES + "." + VARIABLE_ID), ChildScoreMode.None)
      ),
      index
    );
  }

  @Override
  public void deleteEventsWithIdsInFromAllInstances(
    final String index,
    final List<String> eventIdsToDelete,
    final String updateItem
  ) {
    osClient.updateByQueryTask(
      updateItem,
      script(ProcessInstanceScriptFactory.createDeleteEventsWithIdsInScript(), Map.of("eventIdsToDelete", eventIdsToDelete)),
      nested(
        FLOW_NODE_INSTANCES,
        stringTerms(FLOW_NODE_INSTANCES + "." + FLOW_NODE_INSTANCE_ID, eventIdsToDelete),
        ChildScoreMode.None
      ),
      index
    );
  }

  private Script createUpdateStateScript(final String newState) {
    final Map<String, JsonData> scriptParameters = createUpdateStateScriptParamsMap(newState);
    return createDefaultScriptWithPrimitiveParams(ProcessInstanceScriptFactory.createInlineUpdateScript(), scriptParameters);
  }

  private Map<String, JsonData> createUpdateStateScriptParamsMap(final String newState) {
    return Map.of(
      "activeState", json(ACTIVE_STATE),
      "suspendedState", json(SUSPENDED_STATE),
      "newState", json(newState)
    );
  }
}
