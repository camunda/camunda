/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.db.DatabaseConstants.ONBOARDING_INDEX_NAME;

import jakarta.ws.rs.core.Response;
import java.util.Optional;
import lombok.SneakyThrows;
import org.camunda.optimize.AbstractPlatformIT;
import org.camunda.optimize.dto.optimize.OnboardingStateDto;
import org.camunda.optimize.dto.optimize.rest.OnboardingStateRestDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class OnboardingRestIT extends AbstractPlatformIT {

  private static final String KEY_WHATSNEW = "whatsnew";

  @Test
  public void testGetOnboardingState() {
    // given
    assertThat(getOnboardingStateFromDatabase(DEFAULT_USERNAME, KEY_WHATSNEW)).isEmpty();

    // when
    final OnboardingStateRestDto onboardingStateRestDto = getOnboardingState(KEY_WHATSNEW);

    // then
    assertThat(onboardingStateRestDto.isSeen()).isFalse();
  }

  @Test
  public void testGetOnboardingState_invalidKey() {
    // given
    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildGetOnboardingStateForKey("invalid")
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void testGetOnboardingState_unauthorized() {
    // given
    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .withoutAuthentication()
            .buildGetOnboardingStateForKey(KEY_WHATSNEW)
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  @Tag(OPENSEARCH_SINGLE_TEST_FAIL_OK)
  public void testSetOnboardingState() {
    // given
    assertThat(getOnboardingStateFromDatabase(DEFAULT_USERNAME, KEY_WHATSNEW)).isEmpty();

    // when
    embeddedOptimizeExtension
        .getRequestExecutor()
        .buildSetOnboardingStateForKey(KEY_WHATSNEW, true)
        .execute(Response.Status.NO_CONTENT.getStatusCode());

    // then
    final OnboardingStateRestDto onboardingStateRestDto = getOnboardingState(KEY_WHATSNEW);
    assertThat(onboardingStateRestDto.isSeen()).isTrue();
    final Optional<OnboardingStateDto> stateFromElasticsearch =
        getOnboardingStateFromDatabase(DEFAULT_USERNAME, KEY_WHATSNEW);
    assertThat(stateFromElasticsearch)
        .get()
        .hasFieldOrPropertyWithValue(OnboardingStateDto.Fields.key, KEY_WHATSNEW)
        .hasFieldOrPropertyWithValue(OnboardingStateDto.Fields.userId, DEFAULT_USERNAME)
        .hasFieldOrPropertyWithValue(OnboardingStateDto.Fields.seen, true);
  }

  @Test
  public void testSetOnboardingState_invalidKey() {
    // given
    final String invalidKey = "invalid";
    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildSetOnboardingStateForKey(invalidKey, true)
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    assertThat(getOnboardingStateFromDatabase(DEFAULT_USERNAME, invalidKey)).isEmpty();
  }

  @Test
  public void testSetOnboardingState_unauthorized() {
    // given
    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .withoutAuthentication()
            .buildSetOnboardingStateForKey(KEY_WHATSNEW, true)
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  private OnboardingStateRestDto getOnboardingState(final String key) {
    return embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetOnboardingStateForKey(key)
        .execute(OnboardingStateRestDto.class, Response.Status.OK.getStatusCode());
  }

  @SneakyThrows
  private Optional<OnboardingStateDto> getOnboardingStateFromDatabase(
      final String userId, final String key) {
    return databaseIntegrationTestExtension.getDatabaseEntryById(
        ONBOARDING_INDEX_NAME, userId + ":" + key, OnboardingStateDto.class);
  }
}
