/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.zeebe.fetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceRecordDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME;

@Component
@Slf4j
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ZeebeProcessInstanceFetcher extends AbstractZeebeRecordFetcher<ZeebeProcessInstanceRecordDto> {

  public ZeebeProcessInstanceFetcher(final int partitionId,
                                     final OptimizeElasticsearchClient esClient,
                                     final ObjectMapper objectMapper,
                                     final ConfigurationService configurationService) {
    super(partitionId, esClient, objectMapper, configurationService);
  }

  @Override
  protected String getBaseIndexName() {
    return ZEEBE_PROCESS_INSTANCE_INDEX_NAME;
  }

  @Override
  protected Class<ZeebeProcessInstanceRecordDto> getRecordDtoClass() {
    return ZeebeProcessInstanceRecordDto.class;
  }

}
