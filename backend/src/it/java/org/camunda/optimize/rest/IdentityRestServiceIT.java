/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultDto;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;

public class IdentityRestServiceIT {

  @RegisterExtension
  @Order(1)
  protected ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule =
    new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  protected EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  protected EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

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

    final IdentitySearchResultDto searchResult = embeddedOptimizeExtensionRule.getRequestExecutor()
      .buildSearchForIdentities("frodo")
      .execute(IdentitySearchResultDto.class, 200);

    assertThat(searchResult.getTotal(), is(1L));
    assertThat(searchResult.getResult().size(), is(1));
    assertThat(searchResult.getResult().get(0), is(userIdentity));
  }

  @Test
  public void searchForGroup() {
    final GroupDto groupIdentity = new GroupDto("hobbits", "The Hobbits", 4L);
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(groupIdentity);
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(new GroupDto("orcs", "The Orcs", 1000L));

    final IdentitySearchResultDto searchResult = embeddedOptimizeExtensionRule.getRequestExecutor()
      .buildSearchForIdentities("hobbit")
      .execute(IdentitySearchResultDto.class, 200);

    assertThat(searchResult.getTotal(), is(1L));
    assertThat(searchResult.getResult().size(), is(1));
    assertThat(searchResult.getResult().get(0), is(groupIdentity));
  }

  @Test
  public void searchForGroupAndUser() {
    final GroupDto groupIdentity = new GroupDto("group", "The Baggins Group", 2L);
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(groupIdentity);
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(new GroupDto("orcs", "The Orcs", 1000L));
    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(userIdentity);
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(
      new UserDto("otherUser", "Frodo", "NotAHobbit", "not.a.hobbit@camunda.com")
    );

    final IdentitySearchResultDto searchResult = embeddedOptimizeExtensionRule.getRequestExecutor()
      .buildSearchForIdentities("baggins")
      .execute(IdentitySearchResultDto.class, 200);

    assertThat(searchResult.getTotal(), is(2L));
    assertThat(searchResult.getResult().size(), is(2));
    // user is first as name and email contains baggins
    assertThat(searchResult.getResult(), contains(userIdentity, groupIdentity));
  }

  @Test
  public void emptySearchStringReturnsAlphanumericSortingListEmptyNamesLast() {
    final GroupDto groupIdentity = new GroupDto("baggins", "The Baggins Group", 5L);
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(groupIdentity);
    final UserDto userIdentity =
      new UserDto("testUser1", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(userIdentity);
    final UserDto emptyMetaDataUserIdentity = new UserDto("testUser2", null, null, null);
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(emptyMetaDataUserIdentity);

    final IdentitySearchResultDto searchResult = embeddedOptimizeExtensionRule.getRequestExecutor()
      .buildSearchForIdentities("")
      .execute(IdentitySearchResultDto.class, 200);

    assertThat(searchResult.getTotal(), is(3L));
    assertThat(searchResult.getResult().size(), is(3));
    assertThat(searchResult.getResult(), contains(userIdentity, groupIdentity, emptyMetaDataUserIdentity));
  }

  @Test
  public void limitResults() {
    final GroupDto groupIdentity = new GroupDto("baggins", "The Baggins Group", 4L);
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(groupIdentity);
    final UserDto userIdentity =
      new UserDto("testUser1", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(userIdentity);
    final UserDto emptyMetaDataUserIdentity = new UserDto("testUser2", null, null, null);
    embeddedOptimizeExtensionRule.getIdentityService().addIdentity(emptyMetaDataUserIdentity);

    final IdentitySearchResultDto searchResult = embeddedOptimizeExtensionRule.getRequestExecutor()
      .buildSearchForIdentities("", 1)
      .execute(IdentitySearchResultDto.class, 200);
    ;

    assertThat(searchResult.getTotal(), is(3L));
    assertThat(searchResult.getResult().size(), is(1));
    assertThat(searchResult.getResult(), contains(userIdentity));
  }

}
