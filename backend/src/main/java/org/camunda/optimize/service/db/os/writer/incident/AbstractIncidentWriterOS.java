/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer.incident;

import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder.PROCESS_INSTANCE_INDEX;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENTS;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.RequestType;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.service.db.repository.IndexRepository;
import org.camunda.optimize.service.db.schema.ScriptData;
import org.camunda.optimize.service.db.writer.DatabaseWriterUtil;
import org.camunda.optimize.service.db.writer.incident.AbstractIncidentWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
@Conditional(OpenSearchCondition.class)
public abstract class AbstractIncidentWriterOS implements AbstractIncidentWriter {
  private static final Set<String> READ_ONLY_ALIASES = Set.of(PROCESS_INSTANCE_MULTI_ALIAS);
  private final IndexRepository indexRepository;
  private final ObjectMapper objectMapper;

  @Override
  public ImportRequestDto createImportRequestForIncident(
      Map.Entry<String, List<IncidentDto>> incidentsByProcessInstance, final String importName) {
    final List<IncidentDto> incidents = incidentsByProcessInstance.getValue();
    final String processInstanceId = incidentsByProcessInstance.getKey();
    final String processDefinitionKey = incidents.get(0).getDefinitionKey();

    final Map<String, Object> params = new HashMap<>();
    params.put(INCIDENTS, incidents);
    final ScriptData updateScript =
        DatabaseWriterUtil.createScriptData(createInlineUpdateScript(), params, objectMapper);

    final ProcessInstanceDto procInst =
        ProcessInstanceDto.builder()
            .processInstanceId(processInstanceId)
            .dataSource(new EngineDataSourceDto(incidents.get(0).getEngineAlias()))
            .incidents(incidents)
            .build();
    return ImportRequestDto.builder()
        .indexName(getProcessInstanceIndexAliasName(processDefinitionKey))
        .id(processInstanceId)
        .importName(importName)
        .type(RequestType.UPDATE)
        .scriptData(updateScript)
        .source(procInst)
        .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
        .build();
  }

  @Override
  public void createInstanceIndicesFromIncidentsIfMissing(final List<IncidentDto> incidents) {
    final Set<String> definitionKeys =
        incidents.stream().map(IncidentDto::getDefinitionKey).collect(toSet());
    indexRepository.createMissingIndices(PROCESS_INSTANCE_INDEX, READ_ONLY_ALIASES, definitionKeys);
  }
}
