/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.OnboardingStateDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.update.UpdateRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ONBOARDING_INDEX_NAME;

@AllArgsConstructor
@Slf4j
@Component
public class OnboardingStateWriter {
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void upsertOnboardingState(final OnboardingStateDto onboardingStateDto) {
    log.debug("Writing onboarding state [{}] to elasticsearch", onboardingStateDto.getId());
    try {
      final UpdateRequest request = createOnboardingStateUpsert(onboardingStateDto);
      esClient.update(request);
    } catch (IOException e) {
      final String errorMessage = String.format(
        "There were errors while writing the onboarding state [%s].", onboardingStateDto.getId()
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  private UpdateRequest createOnboardingStateUpsert(final OnboardingStateDto onboardingStateDto) {
    return new UpdateRequest()
      .index(ONBOARDING_INDEX_NAME)
      .id(onboardingStateDto.getId())
      .doc(objectMapper.convertValue(onboardingStateDto, Map.class))
      .docAsUpsert(true);
  }
}
