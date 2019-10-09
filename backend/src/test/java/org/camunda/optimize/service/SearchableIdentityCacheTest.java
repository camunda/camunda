/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class SearchableIdentityCacheTest {

  private SearchableIdentityCache cache;

  @Before
  public void before() {
    cache = new SearchableIdentityCache();

    // some unexpected identities, so never use z in test data :)
    cache.addIdentity(new UserDto("zzz", "zzz", "zzz", "zzz"));
    cache.addIdentity(new GroupDto("zzzz", "zzzz"));
  }

  @After
  public void after() {
    cache.close();
  }

  @Test
  public void searchUserById() {
    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    cache.addIdentity(userIdentity);

    final List<IdentityDto> searchResult = cache.searchIdentities(userIdentity.getId());
    assertThat(searchResult.size(), is(1));
  }

  @Test
  public void searchUserById_exactIdMatchBoostedOverMatchesOnOtherFields() {
    final UserDto userIdentity1 = new UserDto("frodo", "Other", "Other", "other.baggins@camunda.com");
    cache.addIdentity(userIdentity1);
    final UserDto userIdentity2 = new UserDto("otherfrodo", "Frodo", "Frodo", "frodo.baggins@camunda.com");
    cache.addIdentity(userIdentity2);

    final List<IdentityDto> searchResult = cache.searchIdentities(userIdentity1.getId());
    assertThat(searchResult.size(), is(2));
    assertThat(searchResult, contains(userIdentity1, userIdentity2));
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

    final List<IdentityDto> searchResult =
      cache.searchIdentities(userIdentity.getFirstName() + " " + userIdentity.getLastName());
    assertThat(searchResult.size(), is(1));
  }

  @Test
  public void searchUserByPartialFirstAndLastName() {

    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    cache.addIdentity(userIdentity);

    // just testing one partial scenario, there is not much benefit in permutating here,
    // minimum term length matches are verified in other tests
    final List<IdentityDto> searchResult =
      cache.searchIdentities(userIdentity.getFirstName().substring(0, 4) + " " + userIdentity.getLastName()
        .substring(0, 2));
    assertThat(searchResult.size(), is(1));
  }

  @Test
  public void searchUserByPartialNameWinsOverPartialId() {

    final UserDto userIdentity1 = new UserDto("testUser1", "Frodo", "Baggins", "f.baggins@camunda.com");
    cache.addIdentity(userIdentity1);
    final UserDto userIdentity2 = new UserDto("frodo", "", "Baggins", "f.baggins@camunda.com");
    cache.addIdentity(userIdentity2);

    final List<IdentityDto> searchResult = cache.searchIdentities("fro");
    assertThat(searchResult.size(), is(2));
    assertThat(searchResult, contains(userIdentity1, userIdentity2));
  }

  @Test
  public void searchUserByPartialNameWinsOverPartialEmail() {

    final UserDto userIdentity1 = new UserDto("testUser1", "Frodo", "Baggins", "f.baggins@camunda.com");
    cache.addIdentity(userIdentity1);
    final UserDto userIdentity2 = new UserDto("testUser2", "", "Baggins", "frodo@camunda.com");
    cache.addIdentity(userIdentity2);

    final List<IdentityDto> searchResult = cache.searchIdentities("Frod");
    assertThat(searchResult.size(), is(2));
    assertThat(searchResult, contains(userIdentity1, userIdentity2));
  }

  @Test
  public void searchUserByFullNameWinsOverPartialEmail() {

    final UserDto userIdentity1 = new UserDto("testUser1", "Frodo", "Baggins", "f.baggins@camunda.com");
    cache.addIdentity(userIdentity1);
    final UserDto userIdentity2 = new UserDto("testUser2", "", "Baggins", "frodo@camunda.com");
    cache.addIdentity(userIdentity2);

    final List<IdentityDto> searchResult = cache.searchIdentities("Frodo");
    assertThat(searchResult.size(), is(2));
    assertThat(searchResult, contains(userIdentity1, userIdentity2));
  }

  @Test
  public void searchGroupById() {
    final GroupDto group = new GroupDto("testGroup", "Test Group");
    cache.addIdentity(group);

    final List<IdentityDto> searchResult = cache.searchIdentities(group.getId());
    assertThat(searchResult.size(), is(1));
  }

  @Test
  public void searchGroupById_exactIdMatchBoostedOverMatchesOnOtherFields() {
    final GroupDto group1 = new GroupDto("testGroup", "Test Group");
    cache.addIdentity(group1);
    final GroupDto group2 = new GroupDto("otherGroup", "testGroup");
    cache.addIdentity(group2);

    final List<IdentityDto> searchResult = cache.searchIdentities(group1.getId());
    assertThat(searchResult.size(), is(2));
    assertThat(searchResult, contains(group1, group2));
  }

  @Test
  public void searchGroupByIdPrefix() {
    final GroupDto group = new GroupDto("testGroup", "HobbitGroup");
    cache.addIdentity(group);

    verifyCaseInsensitiveSearchResults(group.getId().substring(0, 3), 1);
  }

  @Test
  public void searchGroupByName() {
    final GroupDto group = new GroupDto("testGroup", "Test Group");
    cache.addIdentity(group);

    verifyCaseInsensitiveSearchResults(group.getName(), 1);
  }

  @Test
  public void searchGroupByPartialName() {
    final GroupDto group = new GroupDto("testGroup", "Test Group");
    cache.addIdentity(group);

    assertSearchResultsPresentForPartialSearchTerm(group.getName(), 1);
  }

  private void verifyCaseInsensitiveSearchResults(final String searchTerm, final int expectedResultCount) {
    final List<IdentityDto> searchResult = cache.searchIdentities(searchTerm);
    assertThat(searchResult.size(), is(expectedResultCount));
    final List<IdentityDto> searchResultUpperCase = cache.searchIdentities(searchTerm.toUpperCase());
    assertThat(searchResultUpperCase.size(), is(expectedResultCount));
    final List<IdentityDto> searchResultLowerCase = cache.searchIdentities(searchTerm.toLowerCase());
    assertThat(searchResultLowerCase.size(), is(expectedResultCount));
  }

  private void assertSearchResultsPresentForPartialSearchTerm(final String searchTerm, final int mininumTermLength) {
    for (int i = mininumTermLength; i < searchTerm.length(); i++) {
      verifyCaseInsensitiveSearchResults(searchTerm.substring(0, i), 1);
    }
  }

}
