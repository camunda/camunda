/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SearchableIdentityCacheTest {

  private SearchableIdentityCache cache;

  @BeforeEach
  public void before() {
    cache = new SearchableIdentityCache(() -> 200_000L);

    // some unexpected identities, so never use z in test data :)
    cache.addIdentity(new UserDto("zzz", "zzz", "zzz", "zzz"));
    cache.addIdentity(new GroupDto("zzzz", "zzzz", 2L));
  }

  @AfterEach
  public void after() {
    cache.close();
  }

  @Test
  public void searchUserById() {
    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    cache.addIdentity(userIdentity);

    final List<IdentityWithMetadataResponseDto> searchResult = cache.searchIdentities(userIdentity.getId()).getResult();
    assertThat(searchResult).hasSize(1);
  }

  @Test
  public void searchUserById_exactIdMatchBoostedOverMatchesOnOtherFields() {
    final UserDto userIdentity1 = new UserDto("frodo", "Other", "Other", "other.baggins@camunda.com");
    cache.addIdentity(userIdentity1);
    final UserDto userIdentity2 = new UserDto("otherfrodo", "Frodo", "Frodo", "frodo.baggins@camunda.com");
    cache.addIdentity(userIdentity2);

    final List<IdentityWithMetadataResponseDto> searchResult = cache.searchIdentities(userIdentity1.getId())
      .getResult();
    assertThat(searchResult).hasSize(2).containsExactly(userIdentity1, userIdentity2);
  }

  @Test
  public void searchUserByIdPrefix() {
    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    cache.addIdentity(userIdentity);

    verifyCaseInsensitiveSearchResults(userIdentity.getId().substring(0, 3), 1);
  }

  @Test
  public void searchUserByFirstName() {
    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    cache.addIdentity(userIdentity);

    verifyCaseInsensitiveSearchResults(userIdentity.getFirstName(), 1);
  }

  @Test
  public void searchUserByPartialFirstName() {
    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    cache.addIdentity(userIdentity);

    assertSearchResultsPresentForPartialSearchTerm(userIdentity.getFirstName(), 1);
  }

  @Test
  public void searchUserByLastName() {
    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    cache.addIdentity(userIdentity);

    verifyCaseInsensitiveSearchResults(userIdentity.getLastName(), 1);
  }

  @Test
  public void searchUserByPartialLastName() {
    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    cache.addIdentity(userIdentity);

    assertSearchResultsPresentForPartialSearchTerm(userIdentity.getLastName(), 1);
  }

  @Test
  public void searchUserByEmail() {
    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    cache.addIdentity(userIdentity);

    verifyCaseInsensitiveSearchResults(userIdentity.getEmail(), 1);
  }

  @Test
  public void searchUserByFirstAndLastName() {

    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    cache.addIdentity(userIdentity);

    final List<IdentityWithMetadataResponseDto> searchResult =
      cache.searchIdentities(userIdentity.getFirstName() + " " + userIdentity.getLastName()).getResult();
    assertThat(searchResult).hasSize(1);
  }

  @Test
  public void searchUserByPartialFirstAndLastName() {

    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    cache.addIdentity(userIdentity);

    // just testing one partial scenario, there is not much benefit in permutating here,
    // minimum term length matches are verified in other tests
    final List<IdentityWithMetadataResponseDto> searchResult =
      cache.searchIdentities(
        userIdentity.getFirstName().substring(0, 4) + " " + userIdentity.getLastName().substring(0, 2)
      ).getResult();
    assertThat(searchResult).hasSize(1);
  }

  @Test
  public void searchUserByPartialNameWinsOverPartialId() {

    final UserDto userIdentity1 = new UserDto("testUser1", "Frodo", "Baggins", "f.baggins@camunda.com");
    cache.addIdentity(userIdentity1);
    final UserDto userIdentity2 = new UserDto("frodo", "", "Baggins", "f.baggins@camunda.com");
    cache.addIdentity(userIdentity2);

    final List<IdentityWithMetadataResponseDto> searchResult = cache.searchIdentities("fro").getResult();
    assertThat(searchResult).hasSize(2).containsExactly(userIdentity1, userIdentity2);
  }

  @Test
  public void searchUserByPartialNameWinsOverPartialEmail() {

    final UserDto userIdentity1 = new UserDto("testUser1", "Frodo", "Baggins", "f.baggins@camunda.com");
    cache.addIdentity(userIdentity1);
    final UserDto userIdentity2 = new UserDto("testUser2", "", "Baggins", "frodo@camunda.com");
    cache.addIdentity(userIdentity2);

    final List<IdentityWithMetadataResponseDto> searchResult = cache.searchIdentities("Frod").getResult();
    assertThat(searchResult).hasSize(2).containsExactly(userIdentity1, userIdentity2);
  }

  @Test
  public void searchUserByFullNameWinsOverPartialEmail() {

    final UserDto userIdentity1 = new UserDto("testUser1", "Frodo", "Baggins", "f.baggins@camunda.com");
    cache.addIdentity(userIdentity1);
    final UserDto userIdentity2 = new UserDto("testUser2", "", "Baggins", "frodo@camunda.com");
    cache.addIdentity(userIdentity2);

    final List<IdentityWithMetadataResponseDto> searchResult = cache.searchIdentities("Frodo").getResult();
    assertThat(searchResult).hasSize(2).containsExactly(userIdentity1, userIdentity2);
  }

  @Test
  public void searchGroupById() {
    final GroupDto group = new GroupDto("testGroup", "Test Group", 5L);
    cache.addIdentity(group);

    final List<IdentityWithMetadataResponseDto> searchResult = cache.searchIdentities(group.getId()).getResult();
    assertThat(searchResult).containsExactly(group);
  }

  @Test
  public void searchGroupById_exactIdMatchBoostedOverMatchesOnOtherFields() {
    final GroupDto group1 = new GroupDto("testGroup", "Test Group", 5L);
    cache.addIdentity(group1);
    final GroupDto group2 = new GroupDto("otherGroup", "testGroup", 5L);
    cache.addIdentity(group2);

    final List<IdentityWithMetadataResponseDto> searchResult = cache.searchIdentities(group1.getId()).getResult();
    assertThat(searchResult).hasSize(2).containsExactly(group1, group2);
  }

  @Test
  public void searchGroupById_resultsUnaffectedByMemberCountExists() {
    final GroupDto group1 = new GroupDto("testGroup", "Test Group", 5L);
    cache.addIdentity(group1);
    final GroupDto group2 = new GroupDto("otherGroup", "testGroup", null);
    cache.addIdentity(group2);

    final List<IdentityWithMetadataResponseDto> searchResult = cache.searchIdentities(group1.getId()).getResult();
    assertThat(searchResult).hasSize(2).containsExactly(group1, group2);
  }

  @Test
  public void searchGroupByIdPrefix() {
    final GroupDto group = new GroupDto("testGroup", "HobbitGroup", 5L);
    cache.addIdentity(group);

    verifyCaseInsensitiveSearchResults(group.getId().substring(0, 3), 1);
  }

  @Test
  public void searchGroupByName() {
    final GroupDto group = new GroupDto("testGroup", "Test Group", 5L);
    cache.addIdentity(group);

    verifyCaseInsensitiveSearchResults(group.getName(), 1);
  }

  @Test
  public void searchGroupByPartialName() {
    final GroupDto group = new GroupDto("testGroup", "Test Group", 5L);
    cache.addIdentity(group);

    assertSearchResultsPresentForPartialSearchTerm(group.getName(), 1);
  }

  @Test
  public void load100kUsersAndSearch() {
    insert100kUsers();

    final long cacheSizeInBytes = cache.getCacheSizeInBytes();
    final long cacheSizeInMb = cacheSizeInBytes / (1024L * 1024L);
    assertThat(cacheSizeInMb).isLessThan(50L);

    final long beforeSearchMillis = System.currentTimeMillis();
    final List<IdentityWithMetadataResponseDto> searchResult = cache.searchIdentities("Cla").getResult();
    final long afterSearchMillis = System.currentTimeMillis();
    assertThat(searchResult.size()).isPositive();
    assertThat(afterSearchMillis - beforeSearchMillis).isLessThan(1000L);
  }

  @Test
  public void failOnLimitHitAddSingleEntry() {
    cache = new SearchableIdentityCache(() -> 1L);
    cache.addIdentity(new GroupDto("xxxx", "xxxx", 5L));
    assertThrows(MaxEntryLimitHitException.class, () -> cache.addIdentity(new GroupDto("zzzz", "zzzz", 5L)));
  }

  @Test
  public void failOnLimitHitAddEntryBatch() {
    cache = new SearchableIdentityCache(() -> 1L);
    cache.addIdentities(Lists.newArrayList(new GroupDto("xxxx", "xxxx", 5L)));
    assertThrows(
      MaxEntryLimitHitException.class,
      () -> cache.addIdentities(Lists.newArrayList(new GroupDto("zzzz", "zzzz", 5L)))
    );
  }

  @SneakyThrows
  private void insert100kUsers() {
    final List<String> lines = FileUtils.readLines(
      new File(SearchableIdentityCacheTest.class.getResource("/fakeNames100k.csv").toURI()), StandardCharsets.UTF_8
    );
    final List<IdentityWithMetadataResponseDto> users = lines.stream().parallel()
      .map(rawUser -> {
        final String[] properties = rawUser.split(",");
        // use uuid id's, as they are big and bloat the index
        return new UserDto(UUID.randomUUID().toString(), properties[0], properties[1], properties[2]);
      })
      .collect(Collectors.toList());

    cache.addIdentities(users);
  }

  private void verifyCaseInsensitiveSearchResults(final String searchTerm, final int expectedResultCount) {
    final List<IdentityWithMetadataResponseDto> searchResult = cache.searchIdentities(searchTerm).getResult();
    assertThat(searchResult).hasSize(expectedResultCount);
    final List<IdentityWithMetadataResponseDto> searchResultUpperCase = cache.searchIdentities(searchTerm.toUpperCase())
      .getResult();
    assertThat(searchResultUpperCase).hasSize(expectedResultCount);
    final List<IdentityWithMetadataResponseDto> searchResultLowerCase = cache.searchIdentities(searchTerm.toLowerCase())
      .getResult();
    assertThat(searchResultLowerCase).hasSize(expectedResultCount);
  }

  private void assertSearchResultsPresentForPartialSearchTerm(final String searchTerm, final int mininumTermLength) {
    for (int i = mininumTermLength; i < searchTerm.length(); i++) {
      verifyCaseInsensitiveSearchResults(searchTerm.substring(0, i), 1);
    }
  }

}
