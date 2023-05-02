/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.LastKpiEvaluationResultsDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestRequestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessUpdateDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.ProcessOverviewIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_OVERVIEW_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessOverviewWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void updateProcessConfiguration(final String processDefinitionKey, final ProcessUpdateDto processUpdateDto) {
    try {
      final ProcessOverviewDto overviewDto = createNewProcessOverviewDto(processDefinitionKey, processUpdateDto);
      final Map<String, Object> paramMap = new HashMap<>();
      paramMap.put("owner", overviewDto.getOwner());
      paramMap.put("processDefinitionKey", overviewDto.getProcessDefinitionKey());
      paramMap.put("digestEnabled", overviewDto.getDigest().isEnabled());
      final UpdateRequest updateRequest = new UpdateRequest()
        .index(PROCESS_OVERVIEW_INDEX_NAME)
        .id(processDefinitionKey)
        .script(
          new Script(
            ScriptType.INLINE,
            Script.DEFAULT_SCRIPT_LANG,
            getUpdateOverviewScript(),
            paramMap
          )
        )
        .upsert(objectMapper.convertValue(overviewDto, Map.class))
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
      esClient.update(updateRequest);
    } catch (Exception e) {
      final String errorMessage = String.format(
        "There was a problem while updating the process: [%s].",
        processUpdateDto
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  public void updateProcessDigestResults(final String processDefKey, final ProcessDigestDto processDigestDto) {
    try {
      final UpdateRequest updateRequest = new UpdateRequest()
        .index(PROCESS_OVERVIEW_INDEX_NAME)
        .id(processDefKey)
        .script(
          new Script(
            ScriptType.INLINE,
            Script.DEFAULT_SCRIPT_LANG,
            "ctx._source.digest.kpiReportResults = params.kpiReportResults;\n",
            Map.of("kpiReportResults", processDigestDto.getKpiReportResults())
          )
        )
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
      esClient.update(updateRequest);
    } catch (Exception e) {
      final String errorMessage = String.format(
        "There was a problem while updating the digest results for process with key: [%s] and digest results: %s.",
        processDefKey, processDigestDto.getKpiReportResults()
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  public void updateProcessOwnerIfNotSet(final String processDefinitionKey, final String ownerId) {
    try {
      final ProcessUpdateDto processUpdateDto = new ProcessUpdateDto();
      processUpdateDto.setOwnerId(ownerId);
      final ProcessDigestRequestDto processDigestRequestDto = new ProcessDigestRequestDto();
      processUpdateDto.setProcessDigest(processDigestRequestDto);
      final UpdateRequest updateRequest = new UpdateRequest()
        .index(PROCESS_OVERVIEW_INDEX_NAME)
        .id(processDefinitionKey)
        .script(ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams(
          getUpdateOwnerIfNotSetScript(),
          Map.of("owner", ownerId, "processDefinitionKey", processDefinitionKey)
        ))
        .upsert(objectMapper.convertValue(
          createNewProcessOverviewDto(processDefinitionKey, processUpdateDto),
          Map.class
        ))
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
      esClient.update(updateRequest);
    } catch (Exception e) {
      final String errorMessage = String.format(
        "There was a problem while updating the owner for process with key: [%s] and owner ID: %s.",
        processDefinitionKey, ownerId
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  public void updateKpisForProcessDefinitions(Map<String, LastKpiEvaluationResultsDto> definitionKeyToKpis) {
    final BulkRequest bulkRequest = new BulkRequest();
    log.debug("Updating KPI values for process definitions with keys: [{}]", definitionKeyToKpis.keySet());
    for (Map.Entry<String, LastKpiEvaluationResultsDto> entry : definitionKeyToKpis.entrySet()) {
      Map<String, String> reportIdToValue = entry.getValue().getReportIdToValue();
      ProcessOverviewDto processOverviewDto = new ProcessOverviewDto();
      processOverviewDto.setProcessDefinitionKey(entry.getKey());
      processOverviewDto.setDigest(new ProcessDigestDto(
        false,
        Collections.emptyMap()
      ));
      processOverviewDto.setLastKpiEvaluationResults(reportIdToValue);
      UpdateRequest updateRequest = new UpdateRequest()
        .index(PROCESS_OVERVIEW_INDEX_NAME)
        .id(entry.getKey())
        .upsert(objectMapper.convertValue(processOverviewDto, Map.class))
        .script(ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams(
          "ctx._source.lastKpiEvaluationResults = params.lastKpiEvaluationResults;\n",
          Map.of("lastKpiEvaluationResults", reportIdToValue)
        ))
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
      bulkRequest.add(updateRequest);
    }
    ElasticsearchWriterUtil.doBulkRequest(
      esClient,
      bulkRequest,
      new ProcessOverviewIndex().getIndexName(),
      false
    );
  }

  public void deleteProcessOwnerEntry(final String processDefinitionKey) {
    log.info("Removing pending entry " + processDefinitionKey);
    try {
      final DeleteRequest deleteRequest = new DeleteRequest()
        .index(PROCESS_OVERVIEW_INDEX_NAME)
        .id(processDefinitionKey);
      esClient.delete(deleteRequest);
    } catch (IOException e) {
      final String errorMessage =
        String.format(
          "There was a problem while deleting process owner entry for %s",
          processDefinitionKey
        );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  private String getUpdateOverviewScript() {
    // @formatter:off
    return
      "ctx._source.owner = params.owner;\n" +
      "ctx._source.processDefinitionKey = params.processDefinitionKey;\n" +
      "ctx._source.digest.enabled = params.digestEnabled;\n" +
      "if (params.digestCheckIntervalValue != null && params.digestCheckIntervalUnit != null) {\n" +
      "  def alertInterval = [\n" +
      "    'value': params.digestCheckIntervalValue,\n" +
      "    'unit': params.digestCheckIntervalUnit\n" +
      "  ];\n" +
      "  ctx._source.digest.checkInterval = alertInterval;\n" +
      "}\n";
    // @formatter:on
  }

  private String getUpdateOwnerIfNotSetScript() {
    // @formatter:off
    return
      "if (ctx._source.owner == null) {\n" +
      "  ctx._source.owner = params.owner;\n" +
      "}\n" +
      "ctx._source.processDefinitionKey = params.processDefinitionKey;\n";
    // @formatter:on
  }

  private ProcessOverviewDto createNewProcessOverviewDto(final String processDefinitionKey,
                                                         final ProcessUpdateDto processUpdateDto) {
    final ProcessOverviewDto processOverviewDto = new ProcessOverviewDto();
    processOverviewDto.setProcessDefinitionKey(processDefinitionKey);
    processOverviewDto.setOwner(processUpdateDto.getOwnerId());
    processOverviewDto.setDigest(new ProcessDigestDto(
      processUpdateDto.getProcessDigest().isEnabled(),
      Collections.emptyMap()
    ));
    processOverviewDto.setLastKpiEvaluationResults(Collections.emptyMap());
    return processOverviewDto;
  }

}
