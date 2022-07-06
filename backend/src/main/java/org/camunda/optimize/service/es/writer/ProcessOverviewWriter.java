/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.alert.AlertIntervalUnit;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_OVERVIEW_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessOverviewWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void updateProcessDigest(final String processDefinitionKey, final ProcessDigestDto processDigestDto) {
    try {
      final UpdateRequest updateRequest = new UpdateRequest()
        .index(PROCESS_OVERVIEW_INDEX_NAME)
        .id(processDefinitionKey)
        .script(
          new Script(
            ScriptType.INLINE,
            Script.DEFAULT_SCRIPT_LANG,
            getUpdateDigestScript(),
            objectMapper.convertValue(processDigestDto, new TypeReference<>() {
            })
          ))
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
      esClient.update(updateRequest);
    } catch (Exception e) {
      final String errorMessage = String.format(
        "There was a problem while writing the process digest: [%s].",
        processDigestDto
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  public void upsertProcessOwner(final String processDefinitionKey, final String owner) {
    final ProcessOverviewDto newProcessOverview =
      new ProcessOverviewDto(
        owner,
        processDefinitionKey,
        new ProcessDigestDto(new AlertInterval(1, AlertIntervalUnit.WEEKS), false, new HashMap<>())
      );
    try {
      final UpdateRequest updateRequest = new UpdateRequest()
        .index(PROCESS_OVERVIEW_INDEX_NAME)
        .id(processDefinitionKey)
        .script(ElasticsearchWriterUtil.createFieldUpdateScript(
          Set.of(ProcessOverviewDto.Fields.owner),
          newProcessOverview,
          objectMapper
        ))
        .upsert(objectMapper.convertValue(
          newProcessOverview,
          Map.class
        ))
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
      esClient.update(updateRequest);
    } catch (IOException e) {
      final String errorMessage =
        String.format(
          "There was a problem while writing %s as the owner for goals of process: %s",
          owner,
          processDefinitionKey
        );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  public void deleteProcessOwnerEntry(final String processDefinitionKey) {
    try {
      final DeleteRequest deleteRequest = new DeleteRequest()
        .index(PROCESS_OVERVIEW_INDEX_NAME)
        .id(processDefinitionKey);
      esClient.delete(deleteRequest);
    } catch (IOException e) {
      final String errorMessage =
        String.format(
          "There was a problem while deleting process overview for %s",
          processDefinitionKey
        );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  private String getUpdateDigestScript() {
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("digestField", ProcessOverviewDto.Fields.digest)
        .put("checkIntervalField", ProcessDigestDto.Fields.checkInterval)
        .put("enabledField", ProcessDigestDto.Fields.enabled)
        .put("kpiReportResults", ProcessDigestDto.Fields.kpiReportResults)
        .build()
    );

    // @formatter:off
    return substitutor.replace(
  "if (params.${checkIntervalField} != null) {\n" +
          "ctx._source.${digestField}.${checkIntervalField} = params.${checkIntervalField};\n" +
        "}\n" +
        "if (params.${enabledField} != null) {\n" +
          "ctx._source.${digestField}.${enabledField} = params.${enabledField}\n" +
        "}\n" +
        "if (!params.${kpiReportResults}.isEmpty() && params.${kpiReportResults} != null) {\n" +
          "ctx._source.${digestField}.${kpiReportResults} = params.${kpiReportResults}\n" +
        "}\n"
    );
    // @formatter:on
  }
}
