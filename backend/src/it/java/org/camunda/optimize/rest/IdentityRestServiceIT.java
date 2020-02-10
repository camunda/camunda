/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.Lists;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultDto;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.providers.GenericExceptionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_EMAIL_DOMAIN;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FIRSTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_LASTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.KERMIT_GROUP_NAME;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;


public class IdentityRestServiceIT extends AbstractIT {

  @Test
  public void searchForUser_unauthorized() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .withoutAuthentication()
      .buildSearchForIdentities("baggins")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void searchForUser() {
    // given
    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    embeddedOptimizeExtension.getIdentityService().addIdentity(userIdentity);
    embeddedOptimizeExtension.getIdentityService().addIdentity(
      new UserDto("otherId", "Bilbo", "Baggins", "bilbo.baggins@camunda.com")
    );

    // when
    final IdentitySearchResultDto searchResult = embeddedOptimizeExtension.getRequestExecutor()
      .buildSearchForIdentities("frodo")
      .execute(IdentitySearchResultDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(searchResult).isEqualTo(new IdentitySearchResultDto(1L, Lists.newArrayList(userIdentity)));
  }

  @Test
  public void searchForGroup() {
    // given
    final GroupDto groupIdentity = new GroupDto("hobbits", "The Hobbits", 4L);
    embeddedOptimizeExtension.getIdentityService().addIdentity(groupIdentity);
    embeddedOptimizeExtension.getIdentityService().addIdentity(new GroupDto("orcs", "The Orcs", 1000L));

    // when
    final IdentitySearchResultDto searchResult = embeddedOptimizeExtension.getRequestExecutor()
      .buildSearchForIdentities("hobbit")
      .execute(IdentitySearchResultDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(searchResult).isEqualTo(new IdentitySearchResultDto(1L, Lists.newArrayList(groupIdentity)));
  }

  @Test
  public void searchForGroupAndUser() {
    // given
    final GroupDto groupIdentity = new GroupDto("group", "The Baggins Group", 2L);
    embeddedOptimizeExtension.getIdentityService().addIdentity(groupIdentity);
    embeddedOptimizeExtension.getIdentityService().addIdentity(new GroupDto("orcs", "The Orcs", 1000L));
    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    embeddedOptimizeExtension.getIdentityService().addIdentity(userIdentity);
    embeddedOptimizeExtension.getIdentityService().addIdentity(
      new UserDto("otherUser", "Frodo", "NotAHobbit", "not.a.hobbit@camunda.com")
    );

    // when
    final IdentitySearchResultDto searchResult = embeddedOptimizeExtension.getRequestExecutor()
      .buildSearchForIdentities("baggins")
      .execute(IdentitySearchResultDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(searchResult)
      // user is first as name and email contains baggins
      .isEqualTo(new IdentitySearchResultDto(2L, Lists.newArrayList(userIdentity, groupIdentity)));
  }

  @Test
  public void emptySearchStringReturnsAlphanumericSortingListEmptyNamesLast() {
    // given
    final GroupDto groupIdentity = new GroupDto("baggins", "The Baggins Group", 5L);
    embeddedOptimizeExtension.getIdentityService().addIdentity(groupIdentity);
    final UserDto userIdentity =
      new UserDto("testUser1", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    embeddedOptimizeExtension.getIdentityService().addIdentity(userIdentity);
    final UserDto emptyMetaDataUserIdentity = new UserDto("testUser2", null, null, null);
    embeddedOptimizeExtension.getIdentityService().addIdentity(emptyMetaDataUserIdentity);

    // when
    final IdentitySearchResultDto searchResult = embeddedOptimizeExtension.getRequestExecutor()
      .buildSearchForIdentities("")
      .execute(IdentitySearchResultDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(searchResult)
      // user is first as name and email contains baggins
      .isEqualTo(new IdentitySearchResultDto(
        3L, Lists.newArrayList(userIdentity, groupIdentity, emptyMetaDataUserIdentity)
      ));
  }

  @Test
  public void limitResults() {
    // given
    final GroupDto groupIdentity = new GroupDto("baggins", "The Baggins Group", 4L);
    embeddedOptimizeExtension.getIdentityService().addIdentity(groupIdentity);
    final UserDto userIdentity =
      new UserDto("testUser1", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    embeddedOptimizeExtension.getIdentityService().addIdentity(userIdentity);
    final UserDto emptyMetaDataUserIdentity = new UserDto("testUser2", null, null, null);
    embeddedOptimizeExtension.getIdentityService().addIdentity(emptyMetaDataUserIdentity);

    // when
    final IdentitySearchResultDto searchResult = embeddedOptimizeExtension.getRequestExecutor()
      .buildSearchForIdentities("", 1)
      .execute(IdentitySearchResultDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(searchResult)
      // user is first as name and email contains baggins
      .isEqualTo(new IdentitySearchResultDto(
        3L, Lists.newArrayList(userIdentity)
      ));
  }

  @ParameterizedTest
  @MethodSource("identities")
  public void getIdentityById_presentInCache(final IdentityWithMetadataDto expectedIdentity) {
    // given
    embeddedOptimizeExtension.getIdentityService().addIdentity(expectedIdentity);

    // when
    final IdentityWithMetadataDto identity = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetIdentityById(expectedIdentity.getId())
      .execute(IdentityWithMetadataDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(identity).isEqualTo(expectedIdentity);
  }

  @ParameterizedTest
  @MethodSource("identities")
  public void getIdentityById_notPresentInCache(final IdentityWithMetadataDto expectedIdentity) {
    // given
    switch (expectedIdentity.getType()) {
      case USER:
        engineIntegrationExtension.addUser(expectedIdentity.getId(), "password");
        assertThat(
          embeddedOptimizeExtension.getSyncedIdentityCacheService().getUserIdentityById(expectedIdentity.getId())
        ).isEmpty();
        break;
      case GROUP:
        engineIntegrationExtension.createGroup(expectedIdentity.getId());
        assertThat(
          embeddedOptimizeExtension.getSyncedIdentityCacheService().getGroupIdentityById(expectedIdentity.getId())
        ).isEmpty();
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported identity type: " + expectedIdentity.getType());
    }

    // when
    final IdentityWithMetadataDto identity = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetIdentityById(expectedIdentity.getId())
      .execute(IdentityWithMetadataDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(identity).isEqualTo(expectedIdentity);
  }

  @Test
  public void getIdentityById_failOnInvalidId() {
    // when
    final ErrorResponseDto errorResponseDto = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetIdentityById("doesNotExist")
      .execute(ErrorResponseDto.class, Response.Status.NOT_FOUND.getStatusCode());

    // then
    assertThat(errorResponseDto.getErrorCode()).isEqualTo(GenericExceptionMapper.NOT_FOUND_ERROR_CODE);
  }

  @Test
  public void getCurrentUserIdentity() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    final UserDto currentUserDto = embeddedOptimizeExtension.getRequestExecutor()
      .buildCurrentUserIdentity()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute(UserDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(currentUserDto).isEqualTo(new UserDto(
      KERMIT_USER,
      DEFAULT_FIRSTNAME,
      DEFAULT_LASTNAME,
      DEFAULT_USERNAME + DEFAULT_EMAIL_DOMAIN
    ));
  }

  private static Stream<IdentityWithMetadataDto> identities() {
    return Stream.of(
      new UserDto("testUser", DEFAULT_FIRSTNAME, DEFAULT_LASTNAME, "testUser" + DEFAULT_EMAIL_DOMAIN),
      new GroupDto(KERMIT_GROUP_NAME, KERMIT_GROUP_NAME, 0L)
    );
  }


}
