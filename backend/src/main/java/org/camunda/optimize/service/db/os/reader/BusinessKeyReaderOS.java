/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import static org.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
import org.camunda.optimize.service.db.DatabaseConstants;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchDocumentOperations;
import org.camunda.optimize.service.db.reader.BusinessKeyReader;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class BusinessKeyReaderOS implements BusinessKeyReader {

  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

  @Override
  public List<BusinessKeyDto> getBusinessKeysForProcessInstanceIds(
      final Set<String> processInstanceIds) {
    log.debug("Fetching business keys for [{}] process instances", processInstanceIds.size());

    if (processInstanceIds.isEmpty()) {
      return Collections.emptyList();
    }
    SearchRequest.Builder searchReqBuilder =
        new SearchRequest.Builder()
            .index(DatabaseConstants.BUSINESS_KEY_INDEX_NAME)
            .size(LIST_FETCH_LIMIT)
            .query(QueryDSL.ids(processInstanceIds.toArray(new String[0])))
            .scroll(
                RequestDSL.time(
                    String.valueOf(
                        configurationService
                            .getOpenSearchConfiguration()
                            .getScrollTimeoutInSeconds())));

    OpenSearchDocumentOperations.AggregatedResult<Hit<BusinessKeyDto>> searchResponse;
    try {
      searchResponse = osClient.retrieveAllScrollResults(searchReqBuilder, BusinessKeyDto.class);
    } catch (IOException e) {
      final String errorMessage = "Was not able to retrieve business keys!";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    return OpensearchReaderUtil.extractAggregatedResponseValues(searchResponse);
  }
}
