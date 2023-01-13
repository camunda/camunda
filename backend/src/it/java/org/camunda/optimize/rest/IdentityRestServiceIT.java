/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.Lists;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.dto.optimize.rest.UserResponseDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.providers.GenericExceptionMapper;
import org.camunda.optimize.service.util.configuration.users.AuthorizedUserType;
import org.camunda.optimize.test.it.extension.ErrorResponseMock;
import org.camunda.optimize.test.it.extension.MockServerUtil;
import org.camunda.optimize.util.SuperUserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.rest.AuthorizationType.CSV_EXPORT;
import static org.camunda.optimize.dto.optimize.rest.AuthorizationType.ENTITY_EDITOR;
import static org.camunda.optimize.dto.optimize.rest.AuthorizationType.TELEMETRY;
import static org.camunda.optimize.test.engine.AuthorizationClient.GROUP_ID;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_EMAIL_DOMAIN;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FIRSTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_LASTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.KERMIT_GROUP_NAME;
import static org.mockserver.model.HttpRequest.request;

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
    final IdentitySearchResultResponseDto searchResult = identityClient.searchForIdentity("frodo");

    // then
    assertThat(searchResult).isEqualTo(new IdentitySearchResultResponseDto(1L, Lists.newArrayList(userIdentity)));
  }

  @Test
  public void searchForUserWithRoles() {
    // given
    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com", List.of("myRole"));
    embeddedOptimizeExtension.getIdentityService().addIdentity(userIdentity);
    embeddedOptimizeExtension.getIdentityService().addIdentity(
      new UserDto("otherId", "Bilbo", "Baggins", "bilbo.baggins@camunda.com")
    );

    // when
    final IdentitySearchResultResponseDto searchResult = identityClient.searchForIdentity("frodo");

    // then
    assertThat(searchResult).isEqualTo(new IdentitySearchResultResponseDto(1L, Lists.newArrayList(userIdentity)));
  }

  @Test
  public void searchForGroup() {
    // given
    final GroupDto groupIdentity = new GroupDto("hobbits", "The Hobbits", 4L);
    embeddedOptimizeExtension.getIdentityService().addIdentity(groupIdentity);
    embeddedOptimizeExtension.getIdentityService().addIdentity(new GroupDto("orcs", "The Orcs", 1000L));

    // when
    final IdentitySearchResultResponseDto searchResult = identityClient.searchForIdentity("hobbit");

    // then
    assertThat(searchResult).isEqualTo(new IdentitySearchResultResponseDto(1L, Lists.newArrayList(groupIdentity)));
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
    final IdentitySearchResultResponseDto searchResult = identityClient.searchForIdentity("baggins");

    // then
    assertThat(searchResult)
      // user is first as name and email contains baggins
      .isEqualTo(new IdentitySearchResultResponseDto(2L, Lists.newArrayList(userIdentity, groupIdentity)));
  }

  @Test
  public void searchForUser_excludeGroupsFromResults() {
    // given
    final GroupDto groupIdentity = new GroupDto("group", "The Baggins Group", 2L);
    embeddedOptimizeExtension.getIdentityService().addIdentity(groupIdentity);
    final UserDto userIdentity = new UserDto("testUser", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    embeddedOptimizeExtension.getIdentityService().addIdentity(userIdentity);

    // when
    final IdentitySearchResultResponseDto searchResult = identityClient.searchForIdentity("baggins", true);

    // then the result does not contain the user group that matches
    assertThat(searchResult)
      .isEqualTo(new IdentitySearchResultResponseDto(1L, Lists.newArrayList(userIdentity)));
  }

  @Test
  public void searchForGroupAndUser_userMetaDataDisabled() {
    // given
    embeddedOptimizeExtension.getConfigurationService()
      .getUserIdentityCacheConfiguration()
      .setIncludeUserMetaData(false);
    embeddedOptimizeExtension.reloadConfiguration();

    final String groupId = "group";
    final String groupName = "The Baggins Group";
    authorizationClient.createGroupAndGrantOptimizeAccess(groupId, groupName);
    final String userId = "Baggins";
    authorizationClient.addUserAndGrantOptimizeAccess(userId);

    embeddedOptimizeExtension.getUserIdentityCache().synchronizeIdentities();

    // when
    final IdentitySearchResultResponseDto searchResult = identityClient.searchForIdentity("baggins");

    // then
    assertThat(searchResult.getResult())
      // user does not contain meta-data, group name is still there though (as not considered metadata)
      .containsExactlyInAnyOrder(new UserDto(userId), new GroupDto(groupId, groupName));
  }

  @Test
  public void emptySearchStringReturnsAlphanumericSortingList() {
    // given
    final GroupDto groupIdentity = new GroupDto("baggins", "The Baggins Group", 5L);
    embeddedOptimizeExtension.getIdentityService().addIdentity(groupIdentity);
    final UserDto userIdentity =
      new UserDto("testUser1", "Frodo", "Baggins", "frodo.baggins@camunda.com");
    embeddedOptimizeExtension.getIdentityService().addIdentity(userIdentity);
    final UserDto emptyMetaDataUserIdentity = new UserDto("testUser2");
    embeddedOptimizeExtension.getIdentityService().addIdentity(emptyMetaDataUserIdentity);

    // when
    final IdentitySearchResultResponseDto searchResult = identityClient.searchForIdentity("");

    // then
    assertThat(searchResult)
      // user is first as name and email contains baggins, empty user is second because name == id and is `testUser2`
      // group is last because it starts with `Th`
      .isEqualTo(new IdentitySearchResultResponseDto(
        3L, Lists.newArrayList(userIdentity, emptyMetaDataUserIdentity, groupIdentity)
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
    final UserDto emptyMetaDataUserIdentity = new UserDto("testUser2");
    embeddedOptimizeExtension.getIdentityService().addIdentity(emptyMetaDataUserIdentity);

    // when
    final IdentitySearchResultResponseDto searchResult = identityClient.searchForIdentity("", 1);

    // then
    assertThat(searchResult)
      // user is first as name and email contains baggins
      // total count reflects only what the user is being shown, therefore 1
      .isEqualTo(new IdentitySearchResultResponseDto(
        1L, Lists.newArrayList(userIdentity)
      ));
  }

  @ParameterizedTest
  @MethodSource("identities")
  public void getIdentityById_presentInCache(final IdentityWithMetadataResponseDto expectedIdentity) {
    // given
    embeddedOptimizeExtension.getIdentityService().addIdentity(expectedIdentity);

    // when
    final IdentityWithMetadataResponseDto identity = identityClient.getIdentityById(expectedIdentity.getId());

    // then
    assertThat(identity).isEqualTo(expectedIdentity);
  }

  @ParameterizedTest
  @MethodSource("identities")
  public void getIdentityById_notPresentInCache(final IdentityWithMetadataResponseDto expectedIdentity) {
    // given
    switch (expectedIdentity.getType()) {
      case USER:
        authorizationClient.addUserAndGrantOptimizeAccess(expectedIdentity.getId());
        assertThat(
          embeddedOptimizeExtension.getUserIdentityCache().getUserIdentityById(expectedIdentity.getId())
        ).isEmpty();
        break;
      case GROUP:
        authorizationClient.createGroupAndGrantOptimizeAccess(expectedIdentity.getId(), expectedIdentity.getId());
        assertThat(
          embeddedOptimizeExtension.getUserIdentityCache().getGroupIdentityById(expectedIdentity.getId())
        ).isEmpty();
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported identity type: " + expectedIdentity.getType());
    }

    // when
    final IdentityWithMetadataResponseDto identity = identityClient.getIdentityById(expectedIdentity.getId());

    // then
    assertThat(identity).isEqualTo(expectedIdentity);
  }

  @ParameterizedTest
  @MethodSource("identitiesAndAuthorizationResponse")
  public void getIdentityById_notPresentInCache_engineFetchFail(final IdentityWithMetadataResponseDto expectedIdentity,
                                                                final ErrorResponseMock mockedResp) {
    // given
    final HttpRequest engineFetchRequestMatcher;
    switch (expectedIdentity.getType()) {
      case USER:
        authorizationClient.addUserAndGrantOptimizeAccess(expectedIdentity.getId());
        assertThat(
          embeddedOptimizeExtension.getUserIdentityCache().getUserIdentityById(expectedIdentity.getId())
        ).isEmpty();
        engineFetchRequestMatcher = request()
          .withPath(engineIntegrationExtension.getEnginePath() + "/user/" + expectedIdentity.getId() + "/profile");
        break;
      case GROUP:
        authorizationClient.createGroupAndGrantOptimizeAccess(expectedIdentity.getId(), expectedIdentity.getId());
        assertThat(
          embeddedOptimizeExtension.getUserIdentityCache().getGroupIdentityById(expectedIdentity.getId())
        ).isEmpty();
        engineFetchRequestMatcher = request()
          .withPath(engineIntegrationExtension.getEnginePath() + "/group/" + expectedIdentity.getId());
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported identity type: " + expectedIdentity.getType());
    }

    ClientAndServer engineMockServer = useAndGetEngineMockServer();

    mockedResp.mock(engineFetchRequestMatcher, Times.unlimited(), engineMockServer);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetIdentityById(expectedIdentity.getId())
      .execute();

    // then
    assertThat(Response.Status.fromStatusCode(response.getStatus()).getFamily())
      .isNotEqualTo(Response.Status.Family.SUCCESSFUL);
    engineMockServer.verify(engineFetchRequestMatcher);
  }

  @ParameterizedTest
  @MethodSource("identities")
  public void getIdentityById_notPresentInCache_userMetaDataDisabled(final IdentityWithMetadataResponseDto expectedIdentity) {
    // given
    embeddedOptimizeExtension.getConfigurationService()
      .getUserIdentityCacheConfiguration()
      .setIncludeUserMetaData(false);
    embeddedOptimizeExtension.reloadConfiguration();

    final HttpRequest engineFetchRequestMatcher;
    switch (expectedIdentity.getType()) {
      case USER:
        authorizationClient.addUserAndGrantOptimizeAccess(expectedIdentity.getId());
        assertThat(
          embeddedOptimizeExtension.getUserIdentityCache().getUserIdentityById(expectedIdentity.getId())
        ).isEmpty();
        engineFetchRequestMatcher = request()
          .withPath(engineIntegrationExtension.getEnginePath() + "/user/" + expectedIdentity.getId() + "/profile");
        break;
      case GROUP:
        authorizationClient.createGroupAndGrantOptimizeAccess(expectedIdentity.getId(), expectedIdentity.getId());
        assertThat(
          embeddedOptimizeExtension.getUserIdentityCache().getGroupIdentityById(expectedIdentity.getId())
        ).isEmpty();
        engineFetchRequestMatcher = request()
          .withPath(engineIntegrationExtension.getEnginePath() + "/group/" + expectedIdentity.getId());
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported identity type: " + expectedIdentity.getType());
    }

    // when
    final ClientAndServer engineMockServer = useAndGetEngineMockServer();
    final IdentityWithMetadataResponseDto identity = identityClient.getIdentityById(expectedIdentity.getId());

    // then
    assertThat(identity).hasNoNullFieldsOrPropertiesExcept(
      UserDto.Fields.email, UserDto.Fields.firstName, UserDto.Fields.lastName, UserDto.Fields.roles
    );
    assertThat(identity.getName()).isEqualTo(expectedIdentity.getId());
    engineMockServer.verify(engineFetchRequestMatcher);
  }

  @ParameterizedTest
  @MethodSource("identities")
  public void getIdentityById_notPresentInCache_postFetchFailsIfIdentityNotAuthorizedToAccessOptimize(final IdentityWithMetadataResponseDto expectedIdentity) {
    // given
    switch (expectedIdentity.getType()) {
      case USER:
        engineIntegrationExtension.addUser(expectedIdentity.getId(), "password");
        assertThat(
          embeddedOptimizeExtension.getUserIdentityCache().getUserIdentityById(expectedIdentity.getId())
        ).isEmpty();
        break;
      case GROUP:
        engineIntegrationExtension.createGroup(expectedIdentity.getId());
        assertThat(
          embeddedOptimizeExtension.getUserIdentityCache().getGroupIdentityById(expectedIdentity.getId())
        ).isEmpty();
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported identity type: " + expectedIdentity.getType());
    }

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetIdentityById(expectedIdentity.getId())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    switch (expectedIdentity.getType()) {
      case USER:
        assertThat(
          embeddedOptimizeExtension.getUserIdentityCache().getUserIdentityById(expectedIdentity.getId())
        ).isEmpty();
        break;
      case GROUP:
        assertThat(
          embeddedOptimizeExtension.getUserIdentityCache().getGroupIdentityById(expectedIdentity.getId())
        ).isEmpty();
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported identity type: " + expectedIdentity.getType());
    }
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
    final UserResponseDto currentUserDto = identityClient.getCurrentUserIdentity(KERMIT_USER, KERMIT_USER);

    // then
    final UserResponseDto expectedUser = new UserResponseDto(
      new UserDto(
        KERMIT_USER,
        DEFAULT_FIRSTNAME,
        DEFAULT_LASTNAME,
        KERMIT_USER + DEFAULT_EMAIL_DOMAIN
      ), List.of(CSV_EXPORT, ENTITY_EDITOR));
    assertThat(currentUserDto).isEqualTo(expectedUser);
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUPERUSER", "NONE"})
  public void getCurrentUserIdentityForReadOnlyUser(final String authorizationType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeExtension.getConfigurationService()
      .getEntityConfiguration()
      .setAuthorizedUserType(AuthorizedUserType.valueOf(authorizationType));
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    final UserResponseDto currentUserDto = identityClient.getCurrentUserIdentity(KERMIT_USER, KERMIT_USER);

    // then
    final UserResponseDto expectedUser = new UserResponseDto(
      new UserDto(
        KERMIT_USER,
        DEFAULT_FIRSTNAME,
        DEFAULT_LASTNAME,
        KERMIT_USER + DEFAULT_EMAIL_DOMAIN
      ), List.of(CSV_EXPORT));
    assertThat(currentUserDto).isEqualTo(expectedUser);
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUPERUSER", "ALL"})
  public void getCurrentUserIdentityForSuperUserWithEditAuthorization(final String authorizationType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperUserIds(Collections.singletonList(KERMIT_USER));
    embeddedOptimizeExtension.getConfigurationService()
      .getEntityConfiguration()
      .setAuthorizedUserType(AuthorizedUserType.valueOf(authorizationType));
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    final UserResponseDto currentUserDto = identityClient.getCurrentUserIdentity(KERMIT_USER, KERMIT_USER);

    // then
    final UserResponseDto expectedUser = new UserResponseDto(
      new UserDto(
        KERMIT_USER,
        DEFAULT_FIRSTNAME,
        DEFAULT_LASTNAME,
        KERMIT_USER + DEFAULT_EMAIL_DOMAIN
      ), List.of(TELEMETRY, CSV_EXPORT, ENTITY_EDITOR));

    assertThat(currentUserDto).isEqualTo(expectedUser);
  }

  @Test
  public void getCurrentUserIdentityForSuperUserWithReadOnlyAuthorization() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperUserIds(Collections.singletonList(KERMIT_USER));
    embeddedOptimizeExtension.getConfigurationService()
      .getEntityConfiguration()
      .setAuthorizedUserType(AuthorizedUserType.NONE);
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    final UserResponseDto currentUserDto = identityClient.getCurrentUserIdentity(KERMIT_USER, KERMIT_USER);

    // then
    final UserResponseDto expectedUser = new UserResponseDto(
      new UserDto(
        KERMIT_USER,
        DEFAULT_FIRSTNAME,
        DEFAULT_LASTNAME,
        KERMIT_USER + DEFAULT_EMAIL_DOMAIN
      ), List.of(TELEMETRY, CSV_EXPORT));

    assertThat(currentUserDto).isEqualTo(expectedUser);
  }

  @Test
  public void getCurrentUserIdentityWithRolesIfPresent() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeExtension.getUserIdentityCache().synchronizeIdentities();
    final UserDto currentUserIdentity = embeddedOptimizeExtension.getUserIdentityCache().getUserIdentityById(KERMIT_USER)
      .orElseThrow();
    // artificially add the roles to it, to verify they are stored and can be retrieved
    currentUserIdentity.setRoles(List.of("myRole"));
    embeddedOptimizeExtension.getUserIdentityCache().addIdentity(currentUserIdentity);

    // when
    final UserResponseDto currentUserDto = identityClient.getCurrentUserIdentity(KERMIT_USER, KERMIT_USER);

    // then
    final UserResponseDto expectedUser = new UserResponseDto(
      new UserDto(
        KERMIT_USER,
        DEFAULT_FIRSTNAME,
        DEFAULT_LASTNAME,
        KERMIT_USER + DEFAULT_EMAIL_DOMAIN,
        List.of("myRole")
      ), List.of(CSV_EXPORT, ENTITY_EDITOR));
    assertThat(currentUserDto).isEqualTo(expectedUser);
  }

  @Test
  public void getCurrentUserIdentity_userMetaDataDeactivated() {
    // given
    embeddedOptimizeExtension.getConfigurationService()
      .getUserIdentityCacheConfiguration()
      .setIncludeUserMetaData(false);
    embeddedOptimizeExtension.reloadConfiguration();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    final UserResponseDto currentUserDto = identityClient.getCurrentUserIdentity(KERMIT_USER, KERMIT_USER);

    // then only user ID property is set and `getName` returns user ID
    assertThat(currentUserDto).isEqualTo(new UserResponseDto(
      new UserDto(KERMIT_USER),
      List.of(CSV_EXPORT, ENTITY_EDITOR)
    ));
    assertThat(currentUserDto.getUserDto().getName()).isEqualTo(KERMIT_USER);
  }

  @ParameterizedTest
  @EnumSource(SuperUserType.class)
  public void getCurrentUserIdentity_withSuperUserAndSuperGroupAuthorizations(SuperUserType superUserType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    if (superUserType == SuperUserType.USER) {
      embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);
    } else {
      authorizationClient.createKermitGroupAndAddKermitToThatGroup();
      embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperGroupIds().add(GROUP_ID);
    }
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    final UserResponseDto currentUserDto = identityClient.getCurrentUserIdentity(KERMIT_USER, KERMIT_USER);

    // then
    final UserResponseDto expectedUser = new UserResponseDto(
      new UserDto(
        KERMIT_USER,
        DEFAULT_FIRSTNAME,
        DEFAULT_LASTNAME,
        KERMIT_USER + DEFAULT_EMAIL_DOMAIN
      ), List.of(TELEMETRY, CSV_EXPORT, ENTITY_EDITOR));

    assertThat(currentUserDto).isEqualTo(expectedUser);
  }

  private static Stream<IdentityWithMetadataResponseDto> identities() {
    return Stream.of(
      new UserDto("testUser", DEFAULT_FIRSTNAME, DEFAULT_LASTNAME, "testUser" + DEFAULT_EMAIL_DOMAIN),
      new GroupDto(KERMIT_GROUP_NAME, KERMIT_GROUP_NAME, 0L)
    );
  }

  private static Stream<Arguments> identitiesAndAuthorizationResponse() {
    return identities().flatMap(identity -> MockServerUtil.engineMockedErrorResponses()
      .map(errorResponse -> Arguments.of(identity, errorResponse)));
  }

}
