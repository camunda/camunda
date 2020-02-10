/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.apache.http.HttpStatus;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultDto;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;

public class IdentityRestServiceIT extends AbstractIT {

  @Test
  public void searchForUser_unauthorized() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .withoutAuthentication()
      .buildSearchForIdentities("baggins")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), CoreMatchers.is(Response.Status.UNAUTHORIZED.getStatusCode()));
  }

  @Test
  public void searchForUser() {
    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    embeddedOptimizeExtension.getIdentityService().addIdentity(userIdentity);
    embeddedOptimizeExtension.getIdentityService().addIdentity(
      new UserDto("otherId", "Bilbo", "Baggins", "bilbo.baggins@camunda.com")
    );

    final IdentitySearchResultDto searchResult = embeddedOptimizeExtension.getRequestExecutor()
      .buildSearchForIdentities("frodo")
      .execute(IdentitySearchResultDto.class, Response.Status.OK.getStatusCode());

    assertThat(searchResult.getTotal(), is(1L));
    assertThat(searchResult.getResult().size(), is(1));
    assertThat(searchResult.getResult().get(0), is(userIdentity));
  }

  @Test
  public void searchForGroup() {
    final GroupDto groupIdentity = new GroupDto("hobbits", "The Hobbits", 4L);
    embeddedOptimizeExtension.getIdentityService().addIdentity(groupIdentity);
    embeddedOptimizeExtension.getIdentityService().addIdentity(new GroupDto("orcs", "The Orcs", 1000L));

    final IdentitySearchResultDto searchResult = embeddedOptimizeExtension.getRequestExecutor()
      .buildSearchForIdentities("hobbit")
      .execute(IdentitySearchResultDto.class, Response.Status.OK.getStatusCode());

    assertThat(searchResult.getTotal(), is(1L));
    assertThat(searchResult.getResult().size(), is(1));
    assertThat(searchResult.getResult().get(0), is(groupIdentity));
  }

  @Test
  public void searchForGroupAndUser() {
    final GroupDto groupIdentity = new GroupDto("group", "The Baggins Group", 2L);
    embeddedOptimizeExtension.getIdentityService().addIdentity(groupIdentity);
    embeddedOptimizeExtension.getIdentityService().addIdentity(new GroupDto("orcs", "The Orcs", 1000L));
    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    embeddedOptimizeExtension.getIdentityService().addIdentity(userIdentity);
    embeddedOptimizeExtension.getIdentityService().addIdentity(
      new UserDto("otherUser", "Frodo", "NotAHobbit", "not.a.hobbit@camunda.com")
    );

    final IdentitySearchResultDto searchResult = embeddedOptimizeExtension.getRequestExecutor()
      .buildSearchForIdentities("baggins")
      .execute(IdentitySearchResultDto.class, Response.Status.OK.getStatusCode());

    assertThat(searchResult.getTotal(), is(2L));
    assertThat(searchResult.getResult().size(), is(2));
    // user is first as name and email contains baggins
    assertThat(searchResult.getResult(), contains(userIdentity, groupIdentity));
  }

  @Test
  public void emptySearchStringReturnsAlphanumericSortingListEmptyNamesLast() {
    final GroupDto groupIdentity = new GroupDto("baggins", "The Baggins Group", 5L);
    embeddedOptimizeExtension.getIdentityService().addIdentity(groupIdentity);
    final UserDto userIdentity =
      new UserDto("testUser1", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    embeddedOptimizeExtension.getIdentityService().addIdentity(userIdentity);
    final UserDto emptyMetaDataUserIdentity = new UserDto("testUser2", null, null, null);
    embeddedOptimizeExtension.getIdentityService().addIdentity(emptyMetaDataUserIdentity);

    final IdentitySearchResultDto searchResult = embeddedOptimizeExtension.getRequestExecutor()
      .buildSearchForIdentities("")
      .execute(IdentitySearchResultDto.class, Response.Status.OK.getStatusCode());

    assertThat(searchResult.getTotal(), is(3L));
    assertThat(searchResult.getResult().size(), is(3));
    assertThat(searchResult.getResult(), contains(userIdentity, groupIdentity, emptyMetaDataUserIdentity));
  }

  @Test
  public void limitResults() {
    final GroupDto groupIdentity = new GroupDto("baggins", "The Baggins Group", 4L);
    embeddedOptimizeExtension.getIdentityService().addIdentity(groupIdentity);
    final UserDto userIdentity =
      new UserDto("testUser1", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    embeddedOptimizeExtension.getIdentityService().addIdentity(userIdentity);
    final UserDto emptyMetaDataUserIdentity = new UserDto("testUser2", null, null, null);
    embeddedOptimizeExtension.getIdentityService().addIdentity(emptyMetaDataUserIdentity);

    final IdentitySearchResultDto searchResult = embeddedOptimizeExtension.getRequestExecutor()
      .buildSearchForIdentities("", 1)
      .execute(IdentitySearchResultDto.class, Response.Status.OK.getStatusCode());
    ;

    assertThat(searchResult.getTotal(), is(3L));
    assertThat(searchResult.getResult().size(), is(1));
    assertThat(searchResult.getResult(), contains(userIdentity));
  }

}
