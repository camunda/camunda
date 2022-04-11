/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.goals.ProcessGoalsDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_GOALS_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessGoalsWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void updateProcessGoals(ProcessGoalsDto processGoalsDto) {
    try {
      final UpdateRequest updateRequest = new UpdateRequest()
        .index(PROCESS_GOALS_INDEX_NAME)
        .id(processGoalsDto.getProcessDefinitionKey())
        .script(ElasticsearchWriterUtil.createFieldUpdateScript(
          Set.of(ProcessGoalsDto.Fields.durationGoals, ProcessGoalsDto.Fields.processDefinitionKey),
          processGoalsDto,
          objectMapper
        ))
        .upsert(objectMapper.convertValue(processGoalsDto, Map.class))
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
      esClient.update(updateRequest);
    } catch (IOException e) {
      final String errorMessage = String.format(
        "There was a problem while writing the process goals: [%s].",
        processGoalsDto
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  public void updateProcessOwner(final String processDefKey, final String owner) {
    try {
      final ProcessGoalsDto processGoalsDto = new ProcessGoalsDto();
      processGoalsDto.setProcessDefinitionKey(processDefKey);
      processGoalsDto.setOwner(owner);
      processGoalsDto.setDurationGoals(new ArrayList<>());
      final UpdateRequest updateRequest = new UpdateRequest()
        .index(PROCESS_GOALS_INDEX_NAME)
        .id(processDefKey)
        .script(
          new Script(
            ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG,
            "if (params.owner == null) {\n" +
              "  ctx._source.owner = null;\n" +
              "} else {\n" +
              "  ctx._source.owner = params.owner;\n" +
              "}\n" +
              "ctx._source.processDefinitionKey = params.processDefinitionKey;",
            Optional.ofNullable(owner)
              .map(processOwner -> Map.of("owner", owner, "processDefinitionKey", (Object) processDefKey))
              .orElse(Map.of("processDefinitionKey", processDefKey))
          ))
        .upsert(objectMapper.convertValue(processGoalsDto, Map.class))
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
      esClient.update(updateRequest);
    } catch (IOException e) {
      final String errorMessage =
        String.format("There was a problem while writing %s as the owner for goals of process: %s", owner, processDefKey);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }
}
