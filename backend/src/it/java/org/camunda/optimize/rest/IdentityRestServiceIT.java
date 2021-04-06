/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.Lists;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleResponseDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizationType;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.dto.optimize.rest.UserResponseDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.providers.GenericExceptionMapper;
import org.camunda.optimize.service.identity.UserIdentityCacheService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.extension.ErrorResponseMock;
import org.camunda.optimize.test.it.extension.MockServerUtil;
import org.camunda.optimize.util.SuperUserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.EngineConstants.AUTHORIZATION_ENDPOINT;
import static org.camunda.optimize.test.engine.AuthorizationClient.GROUP_ID;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_EMAIL_DOMAIN;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FIRSTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_LASTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.KERMIT_GROUP_NAME;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
  public void searchForGroupAndUser_userMetaDataDisabled() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getUserIdentityCacheConfiguration().setIncludeUserMetaData(false);
    embeddedOptimizeExtension.reloadConfiguration();

    final String groupId = "group";
    final String groupName = "The Baggins Group";
    authorizationClient.createGroupAndGrantOptimizeAccess(groupId, groupName);
    final String userId = "Baggins";
    authorizationClient.addUserAndGrantOptimizeAccess(userId);

    embeddedOptimizeExtension.getUserIdentityCacheService().synchronizeIdentities();

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
    final UserDto emptyMetaDataUserIdentity = new UserDto("testUser2", null, null, null);
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
    final UserDto emptyMetaDataUserIdentity = new UserDto("testUser2", null, null, null);
    embeddedOptimizeExtension.getIdentityService().addIdentity(emptyMetaDataUserIdentity);

    // when
    final IdentitySearchResultResponseDto searchResult = identityClient.searchForIdentity("", 1);

    // then
    assertThat(searchResult)
      // user is first as name and email contains baggins
      // total count is 3 as there are two more identities (the baggins group & testUser2)
      .isEqualTo(new IdentitySearchResultResponseDto(
        3L, Lists.newArrayList(userIdentity)
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
          embeddedOptimizeExtension.getUserIdentityCacheService().getUserIdentityById(expectedIdentity.getId())
        ).isEmpty();
        break;
      case GROUP:
        authorizationClient.createGroupAndGrantOptimizeAccess(expectedIdentity.getId(), expectedIdentity.getId());
        assertThat(
          embeddedOptimizeExtension.getUserIdentityCacheService().getGroupIdentityById(expectedIdentity.getId())
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
          embeddedOptimizeExtension.getUserIdentityCacheService().getUserIdentityById(expectedIdentity.getId())
        ).isEmpty();
        engineFetchRequestMatcher = request()
          .withPath(engineIntegrationExtension.getEnginePath() + "/user/" + expectedIdentity.getId() + "/profile");
        break;
      case GROUP:
        authorizationClient.createGroupAndGrantOptimizeAccess(expectedIdentity.getId(), expectedIdentity.getId());
        assertThat(
          embeddedOptimizeExtension.getUserIdentityCacheService().getGroupIdentityById(expectedIdentity.getId())
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
  @MethodSource("identitiesAndAuthorizationResponse")
  public void noRolesCleanupOnIdentitySyncFailWithError(final IdentityWithMetadataResponseDto expectedIdentity,
                                                        final ErrorResponseMock mockedResp) {
    // given
    UserIdentityCacheService userIdentityCacheService = embeddedOptimizeExtension.getUserIdentityCacheService();

    switch (expectedIdentity.getType()) {
      case USER:
        authorizationClient.addUserAndGrantOptimizeAccess(expectedIdentity.getId());
        assertThat(
          userIdentityCacheService.getUserIdentityById(expectedIdentity.getId())
        ).isEmpty();
        break;
      case GROUP:
        authorizationClient.createGroupAndGrantOptimizeAccess(expectedIdentity.getId(), expectedIdentity.getId());
        assertThat(
          userIdentityCacheService.getGroupIdentityById(expectedIdentity.getId())
        ).isEmpty();
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported identity type: " + expectedIdentity.getType());
    }

    // synchronizing identities to make sure that the newly created identities are accessible in optimize
    userIdentityCacheService.synchronizeIdentities();

    String collectionId = collectionClient.createNewCollection();
    CollectionRoleRequestDto roleDto = new CollectionRoleRequestDto(expectedIdentity, RoleType.EDITOR);
    collectionClient.addRolesToCollection(collectionId, roleDto);

    final HttpRequest engineAuthorizationsRequest = request()
      .withPath(engineIntegrationExtension.getEnginePath() + AUTHORIZATION_ENDPOINT);

    ClientAndServer engineMockServer = useAndGetEngineMockServer();

    mockedResp.mock(engineAuthorizationsRequest, Times.once(), engineMockServer);

    // when
    assertThrows(Exception.class, userIdentityCacheService::synchronizeIdentities);

    // then
    List<CollectionRoleResponseDto> roles = collectionClient.getCollectionRoles(collectionId);

    assertThat(roles).extracting(CollectionRoleResponseDto::getId).contains(roleDto.getId());
    engineMockServer.verify(engineAuthorizationsRequest);
  }

  @ParameterizedTest
  @MethodSource("engineErrors")
  public void syncRetryBackoff(final ErrorResponseMock mockedResp) throws InterruptedException {
    // given
    UserIdentityCacheService userIdentityCacheService = embeddedOptimizeExtension.getUserIdentityCacheService();

    ConfigurationService configurationService = embeddedOptimizeExtension.getConfigurationService();

    // any date, which is not sunday 00:00, so the cron is not triggered
    LocalDateUtil.setCurrentTime(OffsetDateTime.parse("1997-01-27T18:00:00+01:00"));
    configurationService.getUserIdentityCacheConfiguration().setCronTrigger("* 0 * * 0");
    embeddedOptimizeExtension.reloadConfiguration();

    final HttpRequest engineAuthorizationsRequest = request()
      .withPath(engineIntegrationExtension.getEnginePath() + AUTHORIZATION_ENDPOINT);

    ClientAndServer engineMockServer = useAndGetEngineMockServer();

    mockedResp.mock(engineAuthorizationsRequest, Times.unlimited(), engineMockServer);

    final ScheduledExecutorService identitySyncThread = Executors.newSingleThreadScheduledExecutor();

    // when
    try {
      identitySyncThread.execute(userIdentityCacheService::syncIdentitiesWithRetry);
      Thread.sleep(1000);
      engineMockServer.verify(engineAuthorizationsRequest);

      engineMockServer.clear(engineAuthorizationsRequest);
    } finally {
      identitySyncThread.shutdown();
    }

    // then
    boolean termination = identitySyncThread.awaitTermination(30, TimeUnit.SECONDS);
    assertThat(termination).isTrue();
    engineMockServer.verify(engineAuthorizationsRequest);
  }

  @ParameterizedTest
  @MethodSource("identities")
  public void getIdentityById_notPresentInCache_userMetaDataDisabled(final IdentityWithMetadataResponseDto expectedIdentity) {
    // given
    embeddedOptimizeExtension.getConfigurationService().getUserIdentityCacheConfiguration().setIncludeUserMetaData(false);
    embeddedOptimizeExtension.reloadConfiguration();

    final HttpRequest engineFetchRequestMatcher;
    switch (expectedIdentity.getType()) {
      case USER:
        authorizationClient.addUserAndGrantOptimizeAccess(expectedIdentity.getId());
        assertThat(
          embeddedOptimizeExtension.getUserIdentityCacheService().getUserIdentityById(expectedIdentity.getId())
        ).isEmpty();
        engineFetchRequestMatcher = request()
          .withPath(engineIntegrationExtension.getEnginePath() + "/user/" + expectedIdentity.getId() + "/profile");
        break;
      case GROUP:
        authorizationClient.createGroupAndGrantOptimizeAccess(expectedIdentity.getId(), expectedIdentity.getId());
        assertThat(
          embeddedOptimizeExtension.getUserIdentityCacheService().getGroupIdentityById(expectedIdentity.getId())
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
      UserDto.Fields.email, UserDto.Fields.firstName, UserDto.Fields.lastName
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
          embeddedOptimizeExtension.getUserIdentityCacheService().getUserIdentityById(expectedIdentity.getId())
        ).isEmpty();
        break;
      case GROUP:
        engineIntegrationExtension.createGroup(expectedIdentity.getId());
        assertThat(
          embeddedOptimizeExtension.getUserIdentityCacheService().getGroupIdentityById(expectedIdentity.getId())
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
          embeddedOptimizeExtension.getUserIdentityCacheService().getUserIdentityById(expectedIdentity.getId())
        ).isEmpty();
        break;
      case GROUP:
        assertThat(
          embeddedOptimizeExtension.getUserIdentityCacheService().getGroupIdentityById(expectedIdentity.getId())
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
      ), Collections.emptyList());

    assertThat(currentUserDto).isEqualTo(expectedUser);
  }

  @Test
  public void getCurrentUserIdentity_userMetaDataDeactivated() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getUserIdentityCacheConfiguration().setIncludeUserMetaData(false);
    embeddedOptimizeExtension.reloadConfiguration();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    final UserResponseDto currentUserDto = identityClient.getCurrentUserIdentity(KERMIT_USER, KERMIT_USER);

    // then only user ID property is set and `getName` returns user ID
    assertThat(currentUserDto).isEqualTo(new UserResponseDto(new UserDto(KERMIT_USER), Collections.emptyList()));
    assertThat(currentUserDto.getUserDto().getName()).isEqualTo(KERMIT_USER);
  }

  @ParameterizedTest
  @EnumSource(SuperUserType.class)
  public void getCurrentUserIdentity_withSuperUserAndSuperGroupAuthorizations(SuperUserType superUserType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    if (superUserType == SuperUserType.USER) {
      embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(KERMIT_USER);
    } else {
      authorizationClient.createKermitGroupAndAddKermitToThatGroup();
      embeddedOptimizeExtension.getConfigurationService().getSuperGroupIds().add(GROUP_ID);
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
      ), Arrays.asList(AuthorizationType.TELEMETRY, AuthorizationType.IMPORT_EXPORT));

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

  private static Stream<ErrorResponseMock> engineErrors() {
    return MockServerUtil.engineMockedErrorResponses();
  }

}
