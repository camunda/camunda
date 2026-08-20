/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.cloud.CloudUserDto;
import io.camunda.optimize.rest.cloud.AccountsUserAccessTokenProvider;
import io.camunda.optimize.rest.cloud.CCSaaSUserCache;
import io.camunda.optimize.rest.cloud.CCSaaSUserClient;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CCSaaSUserCacheTest {

  public static final String ACCESS_TOKEN = "someToken";

  @Mock CCSaaSUserClient ccSaaSUserClient;
  @Mock AccountsUserAccessTokenProvider accessTokenProvider;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  ConfigurationService configurationService;

  private CCSaaSUserCache underTest;

  @BeforeEach
  public void setup() {
    when(configurationService.getCaches().getCloudUsers().getMaxSize()).thenReturn(10000);
    when(accessTokenProvider.getCurrentUsersAccessToken()).thenReturn(Optional.of(ACCESS_TOKEN));
    underTest = new CCSaaSUserCache(ccSaaSUserClient, accessTokenProvider, configurationService);
  }

  @Test
  public void testCloudUserCacheIsUsedIfMostRecentRequestWasMoreRecentThanConfiguredInterval() {
    // given
    when(configurationService.getCaches().getCloudUsers().getMinFetchIntervalSeconds())
        .thenReturn(600L);
    final CloudUserDto cloudUserDto = createCloudUserWithId("userId");
    when(ccSaaSUserClient.fetchAllCloudUsers(any())).thenReturn(List.of(cloudUserDto));

    // when we make the first request
    Collection<CloudUserDto> allUsers = underTest.getAllUsers();

    // then we get the fetched user and the cloud client was invoked
    assertThat(allUsers).containsExactly(cloudUserDto);
    verify(ccSaaSUserClient, times(1)).fetchAllCloudUsers(ACCESS_TOKEN);

    // when we request for a second time
    allUsers = underTest.getAllUsers();

    // then we get still get the fetched user but the client was not invoked again (so once in
    // total)
    assertThat(allUsers).containsExactly(cloudUserDto);
    verify(ccSaaSUserClient, times(1)).fetchAllCloudUsers(ACCESS_TOKEN);
  }

  @Test
  public void testCloudUserCacheIsInvalidatedWhenRenewed() {
    // given
    when(configurationService.getCaches().getCloudUsers().getMinFetchIntervalSeconds())
        .thenReturn(600L);
    final CloudUserDto cloudUserDto = createCloudUserWithId("userId");
    when(ccSaaSUserClient.fetchAllCloudUsers(any())).thenReturn(List.of(cloudUserDto));

    // when we make the first request
    Collection<CloudUserDto> allUsers = underTest.getAllUsers();

    // then we get the fetched user and the cloud client was invoked
    assertThat(allUsers).containsExactly(cloudUserDto);
    verify(ccSaaSUserClient, times(1)).fetchAllCloudUsers(ACCESS_TOKEN);

    // when the fetch interval is elapsed
    when(configurationService.getCaches().getCloudUsers().getMinFetchIntervalSeconds())
        .thenReturn(-1L);

    // and the second retrieval of all users no longer contains the original user
    when(ccSaaSUserClient.fetchAllCloudUsers(any())).thenReturn(Collections.emptyList());

    // when we request for a second time
    allUsers = underTest.getAllUsers();

    // then the user is not returned as the cache has been invalidated and the cloud client was
    // invoked
    assertThat(allUsers).isEmpty();
    verify(ccSaaSUserClient, times(2)).fetchAllCloudUsers(ACCESS_TOKEN);
  }

  @Test
  public void testCloudUserCacheIsUsedWhenFetchingIndividualUser() {
    // given
    when(configurationService.getCaches().getCloudUsers().getMinFetchIntervalSeconds())
        .thenReturn(600L);
    final String userId = "userId";
    final CloudUserDto cloudUserDto = createCloudUserWithId(userId);
    when(ccSaaSUserClient.fetchAllCloudUsers(ACCESS_TOKEN)).thenReturn(List.of(cloudUserDto));
    // we first populate the cache
    underTest.getAllUsers();

    // when we fetch the user
    Optional<CloudUserDto> fetchedCloudUser = underTest.getUserById(userId);

    // then the user is returned from the cache
    assertThat(fetchedCloudUser).isPresent().get().extracting(user -> user).isEqualTo(cloudUserDto);
    // and no requests to fetch the user from the client are made
    verify(ccSaaSUserClient, times(0)).getCloudUserById(userId, ACCESS_TOKEN);

    // when we fetch a user not in the cache
    final String otherUserId = "someOtherId";
    final CloudUserDto otherCloudUser = createCloudUserWithId(otherUserId);
    when(ccSaaSUserClient.getCloudUserById(otherUserId, ACCESS_TOKEN))
        .thenReturn(Optional.of(otherCloudUser));
    fetchedCloudUser = underTest.getUserById(otherUserId);

    // then the user is returned
    assertThat(fetchedCloudUser)
        .isPresent()
        .get()
        .extracting(user -> user)
        .isEqualTo(otherCloudUser);
    // and a request to fetch the user directly was made
    verify(ccSaaSUserClient, times(1)).getCloudUserById(otherUserId, ACCESS_TOKEN);
  }

  private CloudUserDto createCloudUserWithId(final String userId) {
    final CloudUserDto cloudUserDto = new CloudUserDto();
    cloudUserDto.setUserId(userId);
    cloudUserDto.setName("User Name");
    cloudUserDto.setEmail("some_email@camunda.com");
    cloudUserDto.setRoles(Collections.emptyList());
    return cloudUserDto;
  }
}
