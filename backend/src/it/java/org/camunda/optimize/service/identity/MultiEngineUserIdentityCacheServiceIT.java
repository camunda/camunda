/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.identity;

import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.rest.engine.dto.EngineUserDto;
import org.camunda.optimize.rest.engine.dto.UserCredentialsDto;
import org.camunda.optimize.rest.engine.dto.UserProfileDto;
import org.camunda.optimize.service.AbstractMultiEngineIT;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_APPLICATION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class MultiEngineUserIdentityCacheServiceIT extends AbstractMultiEngineIT {

  public AuthorizationClient defaultEngineAuthorizationClient = new AuthorizationClient(
    engineIntegrationExtension
  );
  public AuthorizationClient secondaryEngineAuthorizationClient = new AuthorizationClient(
    secondaryEngineIntegrationExtension
  );

  @Test
  public void testGrantedUsersFromAllEnginesAreImported() {
    addSecondEngineToConfiguration();

    defaultEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    final String otherEngineUser = "otherUser";
    secondaryEngineAuthorizationClient.addUserAndGrantOptimizeAccess(otherEngineUser);

    final UserIdentityCacheService userIdentityCacheService = getSyncedIdentityCacheService();
    userIdentityCacheService.synchronizeIdentities();

    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER).isPresent(), is(true));
    assertThat(getSyncedIdentityCacheService().getUserIdentityById(otherEngineUser).isPresent(), is(true));
  }

  @Test
  public void testGrantedDuplicateUserFromSecondEnginesIsImportedAlthoughNotGrantedOnDefaultEngine() {
    addSecondEngineToConfiguration();

    defaultEngineAuthorizationClient.addKermitUserWithoutAuthorizations();
    secondaryEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    final UserIdentityCacheService userIdentityCacheService = getSyncedIdentityCacheService();
    userIdentityCacheService.synchronizeIdentities();

    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER).isPresent(), is(true));
  }

  @Test
  public void testDuplicateUserFromSecondEngineDoesNotOverrideUserImportedFromFirstEngine() {
    addSecondEngineToConfiguration();

    EngineUserDto winningUserProfile = createKermitUserDtoWithEmail("Iwin@camunda.com");
    engineIntegrationExtension.addUser(winningUserProfile);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);

    EngineUserDto loosingUserProfile = createKermitUserDtoWithEmail("Iloose@camunda.com");
    secondaryEngineIntegrationExtension.addUser(loosingUserProfile);
    secondaryEngineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);

    final UserIdentityCacheService userIdentityCacheService = getSyncedIdentityCacheService();
    userIdentityCacheService.synchronizeIdentities();

    final Optional<UserDto> userIdentityById = getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER);
    assertThat(userIdentityById.isPresent(), is(true));
    // as engines are iterated in order configured, the user from the first engine is supposed to win
    assertThat(userIdentityById.get().getEmail(), is(winningUserProfile.getProfile().getEmail()));
  }

  @Test
  public void testDuplicateUserFromSecondEngineDoesNotOverrideUserImportedFromFirstEngine_onGlobalAuth() {
    addSecondEngineToConfiguration();

    EngineUserDto winningUserProfile = createKermitUserDtoWithEmail("Iwin@camunda.com");
    defaultEngineAuthorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_APPLICATION);
    engineIntegrationExtension.addUser(winningUserProfile);

    EngineUserDto loosingUserProfile = createKermitUserDtoWithEmail("Iloose@camunda.com");
    secondaryEngineAuthorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_APPLICATION);
    secondaryEngineIntegrationExtension.addUser(loosingUserProfile);

    final UserIdentityCacheService userIdentityCacheService = getSyncedIdentityCacheService();
    userIdentityCacheService.synchronizeIdentities();

    final Optional<UserDto> userIdentityById = getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER);
    assertThat(userIdentityById.isPresent(), is(true));
    // as engines are iterated in order configured, the user from the first engine is supposed to win
    assertThat(userIdentityById.get().getEmail(), is(winningUserProfile.getProfile().getEmail()));
  }

  public EngineUserDto createKermitUserDtoWithEmail(final String email) {
    final UserProfileDto duplicateProfile2 = UserProfileDto.builder()
      .id(KERMIT_USER)
      .email(email)
      .build();
    return new EngineUserDto(duplicateProfile2, new UserCredentialsDto(KERMIT_USER));
  }

  private UserIdentityCacheService getSyncedIdentityCacheService() {
    return embeddedOptimizeExtension.getUserIdentityCacheService();
  }

}
