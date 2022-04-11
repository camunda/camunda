/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import com.google.common.collect.ImmutableSet;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.OnboardingStateDto;
import org.camunda.optimize.service.es.reader.OnboardingStateReader;
import org.camunda.optimize.service.es.writer.OnboardingStateWriter;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.util.Optional;
import java.util.Set;

@AllArgsConstructor
@Component
@Slf4j
public class OnboardingService {
  private static final Set<String> VALID_KEYS = ImmutableSet.of("whatsnew");

  private final OnboardingStateReader onboardingStateReader;
  private final OnboardingStateWriter onboardingStateWriter;

  public Optional<OnboardingStateDto> getStateByKeyAndUser(@NonNull final String key, @NonNull final String userId) {
    final String normalizedKey = normalizeKey(key);
    validateKey(normalizedKey);
    return onboardingStateReader.getOnboardingStateByKeyAndUserId(normalizedKey, userId);
  }

  public void setStateByKeyAndUser(@NonNull final String key, @NonNull final String userId, final boolean seen) {
    final String normalizedKey = normalizeKey(key);
    validateKey(normalizedKey);
    onboardingStateWriter.upsertOnboardingState(new OnboardingStateDto(normalizedKey, userId, seen));
  }

  private String normalizeKey(@NonNull final String key) {
    return key.toLowerCase();
  }

  private void validateKey(final String key) {
    if (!VALID_KEYS.contains(key)) {
      final String message = String.format(
        "The provided key [%s] is not a valid key, valid keys are [%s]", key, VALID_KEYS
      );
      log.error(message);
      throw new NotFoundException(message);
    }
  }
}
