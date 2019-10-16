/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;

public class IdentityRestServiceIT {
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  @Rule
  public RuleChain chain = RuleChain.outerRule(embeddedOptimizeExtensionRule);

  @Test
  public void searchForUser_unauthorized() {
    // when
    final Response response = embeddedOptimizeExtensionRule.getRequestExecutor()
      .withoutAuthentication()
      .buildSearchForIdentities("baggins")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), CoreMatchers.is(401));
  }

  @Test
  public void searchForUser() {
    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(userIdentity);
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(
      new UserDto("otherId", "Bilbo", "Baggins", "bilbo.baggins@camunda.com")
    );

    final List<IdentityDto> searchResult = embeddedOptimizeExtensionRule.getRequestExecutor()
      .buildSearchForIdentities("frodo")
      .executeAndReturnList(IdentityDto.class, 200);

    assertThat(searchResult.size(), is(1));
    assertThat(searchResult.get(0), is(userIdentity));
  }

  @Test
  public void searchForGroup() {
    final GroupDto groupIdentity = new GroupDto("hobbits", "The Hobbits");
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(groupIdentity);
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(new GroupDto("orcs", "The Orcs"));

    final List<IdentityDto> searchResult = embeddedOptimizeExtensionRule.getRequestExecutor()
      .buildSearchForIdentities("hobbit")
      .executeAndReturnList(IdentityDto.class, 200);

    assertThat(searchResult.size(), is(1));
    assertThat(searchResult.get(0), is(groupIdentity));
  }

  @Test
  public void searchForGroupAndUser() {
    final GroupDto groupIdentity = new GroupDto("group", "The Baggins Group");
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(groupIdentity);
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(new GroupDto("orcs", "The Orcs"));
    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(userIdentity);
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(
      new UserDto("otherUser", "Frodo", "NotAHobbit", "not.a.hobbit@camunda.com")
    );

    final List<IdentityDto> searchResult = embeddedOptimizeExtensionRule.getRequestExecutor()
      .buildSearchForIdentities("baggins")
      .executeAndReturnList(IdentityDto.class, 200);

    assertThat(searchResult.size(), is(2));
    // user is first as name and email contains baggins
    assertThat(searchResult, contains(userIdentity, groupIdentity));
  }

  @Test
  public void emptySearchStringReturnsAlphanumericSortingListEmptyNamesLast() {
    final GroupDto groupIdentity = new GroupDto("baggins", "The Baggins Group");
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(groupIdentity);
    final UserDto userIdentity =
      new UserDto("testUser1", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(userIdentity);
    final UserDto emptyMetaDataUserIdentity = new UserDto("testUser2", null, null, null);
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(emptyMetaDataUserIdentity);

    final List<IdentityDto> searchResult = embeddedOptimizeExtensionRule.getRequestExecutor()
      .buildSearchForIdentities("")
      .executeAndReturnList(IdentityDto.class, 200);

    assertThat(searchResult.size(), is(3));
    assertThat(searchResult, contains(userIdentity, groupIdentity, emptyMetaDataUserIdentity));
  }

  @Test
  public void limitResults() {
    final GroupDto groupIdentity = new GroupDto("baggins", "The Baggins Group");
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(groupIdentity);
    final UserDto userIdentity =
      new UserDto("testUser1", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(userIdentity);
    final UserDto emptyMetaDataUserIdentity = new UserDto("testUser2", null, null, null);
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(emptyMetaDataUserIdentity);

    final List<IdentityDto> searchResult = embeddedOptimizeExtensionRule.getRequestExecutor()
      .buildSearchForIdentities("", 1)
      .executeAndReturnList(IdentityDto.class, 200);

    assertThat(searchResult.size(), is(1));
    assertThat(searchResult, contains(userIdentity));
  }

}
