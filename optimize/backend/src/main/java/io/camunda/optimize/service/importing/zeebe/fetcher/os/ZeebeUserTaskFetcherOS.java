/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.fetcher.os;

import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_USER_TASK_INDEX_NAME;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskRecordDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.importing.zeebe.db.ZeebeUserTaskFetcher;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.slf4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Conditional(OpenSearchCondition.class)
public class ZeebeUserTaskFetcherOS extends AbstractZeebeRecordFetcherOS<ZeebeUserTaskRecordDto>
    implements ZeebeUserTaskFetcher {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ZeebeUserTaskFetcherOS.class);

  protected ZeebeUserTaskFetcherOS(
      final int partitionId,
      final OptimizeOpenSearchClient osClient,
      final ObjectMapper objectMapper,
      final ConfigurationService configurationService) {
    super(partitionId, osClient, objectMapper, configurationService);
  }

  @Override
  protected String getBaseIndexName() {
    return ZEEBE_USER_TASK_INDEX_NAME;
  }

  @Override
  protected Class<ZeebeUserTaskRecordDto> getRecordDtoClass() {
    return ZeebeUserTaskRecordDto.class;
  }
}
