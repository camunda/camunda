/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.fetcher.es;

import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_VARIABLE_INDEX_NAME;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.importing.zeebe.db.ZeebeVariableFetcher;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.slf4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Conditional(ElasticSearchCondition.class)
public class ZeebeVariableFetcherES extends AbstractZeebeRecordFetcherES<ZeebeVariableRecordDto>
    implements ZeebeVariableFetcher {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ZeebeVariableFetcherES.class);

  public ZeebeVariableFetcherES(
      final int partitionId,
      final OptimizeElasticsearchClient esClient,
      final ObjectMapper objectMapper,
      final ConfigurationService configurationService) {
    super(partitionId, esClient, objectMapper, configurationService);
  }

  @Override
  protected String getBaseIndexName() {
    return ZEEBE_VARIABLE_INDEX_NAME;
  }

  @Override
  protected Class<ZeebeVariableRecordDto> getRecordDtoClass() {
    return ZeebeVariableRecordDto.class;
  }
}
