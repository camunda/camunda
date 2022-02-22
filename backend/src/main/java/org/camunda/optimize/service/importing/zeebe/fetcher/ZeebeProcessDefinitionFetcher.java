/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.zeebe.fetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.zeebe.definition.ZeebeProcessDefinitionRecordDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Set;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ZEEBE_PROCESS_DEFINITION_INDEX_NAME;

@Component
@Slf4j
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ZeebeProcessDefinitionFetcher extends AbstractZeebeRecordFetcher<ZeebeProcessDefinitionRecordDto> {

  private static final Set<String> INTENTS = Set.of(ProcessIntent.CREATED.name());

  public ZeebeProcessDefinitionFetcher(final int partitionId,
                                       final OptimizeElasticsearchClient esClient,
                                       final ObjectMapper objectMapper,
                                       final ConfigurationService configurationService) {
    super(partitionId, esClient, objectMapper, configurationService);
  }

  @Override
  protected String getBaseIndexName() {
    return ZEEBE_PROCESS_DEFINITION_INDEX_NAME;
  }

  @Override
  protected Set<String> getIntentsForRecordType() {
    return INTENTS;
  }

  @Override
  protected Class<ZeebeProcessDefinitionRecordDto> getRecordDtoClass() {
    return ZeebeProcessDefinitionRecordDto.class;
  }

}
