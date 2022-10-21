/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import org.camunda.optimize.dto.optimize.cloud.CloudUserDto;
import org.camunda.optimize.rest.cloud.AccountsUserAccessTokenProvider;
import org.camunda.optimize.rest.cloud.CCSaaSUserClient;
import org.camunda.optimize.rest.cloud.CloudUsersService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CloudUserCacheTest {

  public static final String ACCESS_TOKEN = "someToken";

  @Mock
  CCSaaSUserClient ccSaaSUserClient;
  @Mock
  AccountsUserAccessTokenProvider accessTokenProvider;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  ConfigurationService configurationService;

  private CloudUsersService underTest;

  @BeforeEach
  public void setup() {
    when(configurationService.getCaches().getCloudUsers().getMaxSize()).thenReturn(10000);
    when(accessTokenProvider.getCurrentUsersAccessToken()).thenReturn(Optional.of(ACCESS_TOKEN));
    underTest = new CloudUsersService(ccSaaSUserClient, accessTokenProvider, configurationService);
  }

  @Test
  public void testCloudUserCacheIsUsedIfMostRecentRequestWasMoreRecentThanConfiguredInterval() {
    // given
    when(configurationService.getCaches().getCloudUsers().getMinFetchIntervalSeconds()).thenReturn(600L);
    final CloudUserDto cloudUserDto = createCloudUserWithId("userId");
    when(ccSaaSUserClient.fetchAllCloudUsers(any())).thenReturn(List.of(cloudUserDto));

    // when we make the first request
    Collection<CloudUserDto> allUsers = underTest.getAllUsers();

    // then we get the fetched user and the cloud client was invoked
    assertThat(allUsers).containsExactly(cloudUserDto);
    verify(ccSaaSUserClient, times(1)).fetchAllCloudUsers(ACCESS_TOKEN);

    // when we request for a second time
    allUsers = underTest.getAllUsers();

    // then we get still get the fetched user but the client was not invoked again (so once in total)
    assertThat(allUsers).containsExactly(cloudUserDto);
    verify(ccSaaSUserClient, times(1)).fetchAllCloudUsers(ACCESS_TOKEN);
  }

  @Test
  public void testCloudUserIsFetchedForIndividualUser() {
    // given
    when(configurationService.getCaches().getCloudUsers().getMinFetchIntervalSeconds()).thenReturn(600L);
    final String userId = "userId";
    final CloudUserDto cloudUserDto = createCloudUserWithId(userId);
    when(ccSaaSUserClient.getCloudUserById(userId, ACCESS_TOKEN)).thenReturn(Optional.of(cloudUserDto));

    // when we fetch the user
    Optional<CloudUserDto> fetchedCloudUser = underTest.getUserById(userId);

    // then the user is returned from the client
    assertThat(fetchedCloudUser).isPresent().get().isEqualTo(cloudUserDto);
    verify(ccSaaSUserClient, times(1)).getCloudUserById(userId, ACCESS_TOKEN);
    // and the user is now in the cache
    assertThat(underTest.getAllUsers()).containsExactly(cloudUserDto);
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
