/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.OnboardingStateDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ONBOARDING_INDEX_NAME;

@RequiredArgsConstructor
@Component
@Slf4j
public class OnboardingStateReader {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public Optional<OnboardingStateDto> getOnboardingStateByKeyAndUserId(final String key, final String userId) {
    log.debug("Fetching onboarding state by key [{}] and userId [{}]", key, userId);

    final String onboardingStateEntryId = new OnboardingStateDto(key, userId).getId();
    final GetRequest getRequest = new GetRequest(ONBOARDING_INDEX_NAME).id(onboardingStateEntryId);

    OnboardingStateDto result = null;
    try {
      final GetResponse getResponse = esClient.get(getRequest);
      if (getResponse.isExists()) {
        result = objectMapper.readValue(getResponse.getSourceAsString(), OnboardingStateDto.class);
      }
    } catch (IOException e) {
      final String errorMessage = String.format(
        "There was an error while reading the onboarding state by key [%s] and userId [%s].", key, userId
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    return Optional.ofNullable(result);
  }

}
