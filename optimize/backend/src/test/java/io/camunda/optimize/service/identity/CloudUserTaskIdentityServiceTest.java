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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.GroupDto;
import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CloudUserTaskIdentityServiceTest {

  public static final String EXTERNAL_ID = "externalId";
  public static final UserDto TEST_USER_1 =
      new UserDto("123", "Donna Noble", "donna@email.com", Collections.emptyList());
  public static final UserDto TEST_USER_2 =
      new UserDto("456", "John Smith", "john@email.com", Collections.emptyList());

  @Mock AbstractIdentityService identityService;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  ConfigurationService configurationService;

  private UserTaskIdentityService underTest;

  @BeforeEach
  public void setup() {
    when(configurationService.getCaches().getUsers().getMaxSize()).thenReturn(10000);
    when(configurationService.getCaches().getUsers().getDefaultTtlMillis()).thenReturn(600000);
    underTest = new UserTaskIdentityService(identityService, configurationService);
  }

  @Test
  public void searchAmongIdentitiesWithIdsEmptySearchTermReturnsAllUsers() {
    // given
    when(identityService.getUsersById(any()))
        .thenReturn(
            Stream.of(TEST_USER_1, TEST_USER_2)
                .map(IdentityWithMetadataResponseDto.class::cast)
                .toList());
    final List<String> idsToSearchAmongst =
        Stream.of(TEST_USER_1.getId(), TEST_USER_2.getId(), EXTERNAL_ID).toList();

    // when
    final IdentitySearchResultResponseDto result =
        underTest.searchAmongIdentitiesWithIds("", idsToSearchAmongst, IdentityType.USER, 10);

    // then a result is returned for all requested IDs
    assertThat(result.getResult())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(TEST_USER_1, TEST_USER_2, new UserDto(EXTERNAL_ID));
  }

  @Test
  public void searchAmongIdentitiesWithIdsEmptySearchTermReturnsAllGroups() {
    // given
    final List<String> idsToSearchAmongst = Stream.of(EXTERNAL_ID).toList();

    // when
    final IdentitySearchResultResponseDto result =
        underTest.searchAmongIdentitiesWithIds("", idsToSearchAmongst, IdentityType.GROUP, 10);

    // then
    assertThat(result.getResult())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(new GroupDto(EXTERNAL_ID));
  }

  @Test
  public void searchAmongIdentitiesWithIdsWithMatchInInternalCache() {
    // given
    when(identityService.searchForIdentitiesAsUser(any(), any(), anyInt(), anyBoolean()))
        .thenReturn(new IdentitySearchResultResponseDto(List.of(TEST_USER_1)));
    final List<String> idsToSearchAmongst =
        Stream.of(TEST_USER_1.getId(), TEST_USER_2.getId(), EXTERNAL_ID)
            .collect(Collectors.toList());

    // when
    final IdentitySearchResultResponseDto result =
        underTest.searchAmongIdentitiesWithIds("test", idsToSearchAmongst, IdentityType.USER, 10);

    // then only the match is returned
    assertThat(result.getResult())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(TEST_USER_1);
  }

  @Test
  public void searchAmongIdentitiesWithIdsWithMatchInExternalCache() {
    // given search for external user so that user is present in external cache
    when(identityService.getUserById(EXTERNAL_ID)).thenReturn(Optional.empty());
    underTest.getIdentityByIdAndType(EXTERNAL_ID, IdentityType.USER);
    when(identityService.searchForIdentitiesAsUser(any(), any(), anyInt(), anyBoolean()))
        .thenReturn(new IdentitySearchResultResponseDto(Collections.emptyList()));
    final List<String> idsToSearchAmongst =
        Stream.of(TEST_USER_1.getId(), TEST_USER_2.getId(), EXTERNAL_ID)
            .collect(Collectors.toList());

    // when
    final IdentitySearchResultResponseDto result =
        underTest.searchAmongIdentitiesWithIds(
            EXTERNAL_ID, idsToSearchAmongst, IdentityType.USER, 10);

    // then the cache match is returned
    assertThat(result.getResult())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(new UserDto(EXTERNAL_ID));
  }

  @Test
  public void searchAmongIdentitiesWithIdsNoMatchesInInternalOrExternalCache() {
    // given
    when(identityService.searchForIdentitiesAsUser(any(), any(), anyInt(), anyBoolean()))
        .thenReturn(new IdentitySearchResultResponseDto(Collections.emptyList()));
    final List<String> idsToSearchAmongst =
        Stream.of(TEST_USER_1.getId(), TEST_USER_2.getId(), EXTERNAL_ID)
            .collect(Collectors.toList());

    // when
    final IdentitySearchResultResponseDto result =
        underTest.searchAmongIdentitiesWithIds("test", idsToSearchAmongst, IdentityType.USER, 10);

    // then
    assertThat(result.getResult()).isEmpty();
  }

  @Test
  public void searchAmongIdentitiesWithIdsResultLimitApplied() {
    // given 2 matches
    when(identityService.searchForIdentitiesAsUser(any(), any(), anyInt(), anyBoolean()))
        .thenReturn(new IdentitySearchResultResponseDto(List.of(TEST_USER_1, TEST_USER_2)));
    final List<String> idsToSearchAmongst =
        Stream.of(TEST_USER_1.getId(), TEST_USER_2.getId(), EXTERNAL_ID)
            .collect(Collectors.toList());

    // when
    final IdentitySearchResultResponseDto result =
        underTest.searchAmongIdentitiesWithIds("test", idsToSearchAmongst, IdentityType.USER, 1);

    // then only one match is returned due to the resultLimit
    assertThat(result.getResult())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(TEST_USER_1);
  }

  @Test
  public void getIdentitiesWithMatchInInternalCache() {
    // given
    when(identityService.getUsersById(new HashSet<>(List.of(TEST_USER_1.getId()))))
        .thenReturn(
            Stream.of(TEST_USER_1)
                .map(IdentityWithMetadataResponseDto.class::cast)
                .collect(Collectors.toList()));

    // when
    final List<IdentityWithMetadataResponseDto> result =
        underTest.getIdentities(
            Stream.of(new IdentityDto(TEST_USER_1.getId(), IdentityType.USER)).toList());

    // then
    assertThat(result).usingRecursiveFieldByFieldElementComparator().containsExactly(TEST_USER_1);
  }

  @Test
  public void getIdentitiesNoMatchReturnsDefaultAndAddsToExternalCache() {
    // given
    when(identityService.getUsersById(Set.of(EXTERNAL_ID))).thenReturn(Collections.emptyList());

    // when
    List<IdentityWithMetadataResponseDto> result =
        underTest.getIdentities(
            Stream.of(new IdentityDto(EXTERNAL_ID, IdentityType.USER)).toList());

    // then
    assertThat(result)
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(new UserDto(EXTERNAL_ID));
    verify(identityService, times(1)).getUsersById(Set.of(EXTERNAL_ID));

    // when searching again for the same user
    reset(identityService);
    result =
        underTest.getIdentities(
            Stream.of(new IdentityDto(EXTERNAL_ID, IdentityType.USER)).toList());

    // then the default is returned from cache, so identityService is not called again
    assertThat(result)
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(new UserDto(EXTERNAL_ID));
    verify(identityService, never()).getUsersById(Set.of(EXTERNAL_ID));
  }

  @Test
  public void getIdentitiesOneInternalOneExternal() {
    // given
    when(identityService.getUsersById(Set.of(TEST_USER_1.getId(), EXTERNAL_ID)))
        .thenReturn(
            Stream.of(TEST_USER_1)
                .map(IdentityWithMetadataResponseDto.class::cast)
                .collect(Collectors.toList()));

    // when
    final List<IdentityWithMetadataResponseDto> result =
        underTest.getIdentities(
            Stream.of(TEST_USER_1, new IdentityDto(EXTERNAL_ID, IdentityType.USER)).toList());

    // then
    assertThat(result)
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(TEST_USER_1, new UserDto(EXTERNAL_ID));
  }

  @Test
  public void getIdentitiesGroupsReturnedAsDefaultAndAddedToExternalCache() {
    // given
    when(identityService.getGroupsById(Set.of(EXTERNAL_ID))).thenReturn(Collections.emptyList());

    // when
    List<IdentityWithMetadataResponseDto> result =
        underTest.getIdentities(
            Stream.of(new IdentityDto(EXTERNAL_ID, IdentityType.GROUP)).toList());

    // then
    assertThat(result)
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(new GroupDto(EXTERNAL_ID));
    verify(identityService, times(1)).getGroupsById(Set.of(EXTERNAL_ID));

    // when searching again for the same user
    reset(identityService);
    result =
        underTest.getIdentities(
            Stream.of(new IdentityDto(EXTERNAL_ID, IdentityType.GROUP)).toList());

    // then the default is returned from cache, so identityService is not called again
    assertThat(result)
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(new GroupDto(EXTERNAL_ID));
    verify(identityService, never()).getGroupsById(Set.of(EXTERNAL_ID));
  }

  @Test
  public void getIdentityByIdAndTypeWithMatchInInternalCache() {
    // given
    when(identityService.getUserById(TEST_USER_1.getId())).thenReturn(Optional.of(TEST_USER_1));

    // when
    final Optional<IdentityWithMetadataResponseDto> result =
        underTest.getIdentityByIdAndType(TEST_USER_1.getId(), IdentityType.USER);

    // then
    assertThat(result).contains(TEST_USER_1);
  }

  @Test
  public void getIdentityByIdAndTypeNoMatchReturnsDefaultAndAddsToExternalCache() {
    // given
    when(identityService.getUserById(EXTERNAL_ID)).thenReturn(Optional.empty());

    // when
    Optional<IdentityWithMetadataResponseDto> result =
        underTest.getIdentityByIdAndType(EXTERNAL_ID, IdentityType.USER);

    // then
    assertThat(result).contains(new UserDto(EXTERNAL_ID));
    verify(identityService, times(1)).getUserById(EXTERNAL_ID);

    // when requesting the same ID again
    reset(identityService);
    result = underTest.getIdentityByIdAndType(EXTERNAL_ID, IdentityType.USER);

    // then the result is retrieved from the external cache and identityService is not used again
    assertThat(result).contains(new UserDto(EXTERNAL_ID));
    verify(identityService, never()).getUserById(EXTERNAL_ID);
  }

  @Test
  public void getIdentityByIdAndTypeGroupReturnsDefaultAndAddsToExternalCache() {
    // given
    when(identityService.getGroupById(EXTERNAL_ID)).thenReturn(Optional.empty());

    // when
    Optional<IdentityWithMetadataResponseDto> result =
        underTest.getIdentityByIdAndType(EXTERNAL_ID, IdentityType.GROUP);

    // then
    assertThat(result).contains(new GroupDto(EXTERNAL_ID));
    verify(identityService, times(1)).getGroupById(EXTERNAL_ID);

    // when requesting the same ID again
    reset(identityService);
    result = underTest.getIdentityByIdAndType(EXTERNAL_ID, IdentityType.GROUP);

    // then the result is retrieved from the external cache and identityService is not used again
    assertThat(result).contains(new GroupDto(EXTERNAL_ID));
    verify(identityService, never()).getGroupById(EXTERNAL_ID);
  }
}
