/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.rest.engine.dto.EngineUserDto;
import org.camunda.optimize.rest.engine.dto.UserCredentialsDto;
import org.camunda.optimize.rest.engine.dto.UserProfileDto;
import org.camunda.optimize.service.AbstractMultiEngineIT;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_APPLICATION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public class MultiEnginePlatformUserIdentityCacheServiceIT extends AbstractMultiEngineIT {

  @Test
  public void grantedUsersFromAllEnginesAreImported() {
    // given
    addSecondEngineToConfiguration();

    defaultEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    final String otherEngineUser = "otherUser";
    secondaryEngineAuthorizationClient.addUserAndGrantOptimizeAccess(otherEngineUser);

    // when
    getSyncedIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER)).isPresent();
    assertThat(getSyncedIdentityCacheService().getUserIdentityById(otherEngineUser)).isPresent();
  }

  @Test
  public void grantedDuplicateUserFromSecondEnginesIsImportedAlthoughNotGrantedOnDefaultEngine() {
    // given
    addSecondEngineToConfiguration();

    defaultEngineAuthorizationClient.addKermitUserWithoutAuthorizations();
    secondaryEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    getSyncedIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER)).isPresent();
  }

  @Test
  public void duplicateUserFromSecondEngineDoesNotOverrideUserImportedFromFirstEngine() {
    // given
    addSecondEngineToConfiguration();

    EngineUserDto winningUserProfile = createKermitUserDtoWithEmail("Iwin@camunda.com");
    engineIntegrationExtension.addUser(winningUserProfile);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);

    EngineUserDto loosingUserProfile = createKermitUserDtoWithEmail("Iloose@camunda.com");
    secondaryEngineIntegrationExtension.addUser(loosingUserProfile);
    secondaryEngineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);

    // when
    getSyncedIdentityCacheService().synchronizeIdentities();

    // then
    final Optional<UserDto> userIdentityById = getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER);
    // as engines are iterated in order configured, the user from the first engine is supposed to win
    assertThat(userIdentityById)
      .isPresent().get()
      .extracting(UserDto::getEmail)
      .isEqualTo(winningUserProfile.getProfile().getEmail());
  }

  @Test
  public void duplicateUserFromSecondEngineDoesNotOverrideUserImportedFromFirstEngine_onGlobalAuth() {
    // given
    addSecondEngineToConfiguration();

    EngineUserDto winningUserProfile = createKermitUserDtoWithEmail("Iwin@camunda.com");
    defaultEngineAuthorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_APPLICATION);
    engineIntegrationExtension.addUser(winningUserProfile);

    EngineUserDto loosingUserProfile = createKermitUserDtoWithEmail("Iloose@camunda.com");
    secondaryEngineAuthorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_APPLICATION);
    secondaryEngineIntegrationExtension.addUser(loosingUserProfile);

    // when
    getSyncedIdentityCacheService().synchronizeIdentities();

    // then
    final Optional<UserDto> userIdentityById = getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER);
    // as engines are iterated in order configured, the user from the first engine is supposed to win
    assertThat(userIdentityById)
      .isPresent().get()
      .extracting(UserDto::getEmail)
      .isEqualTo(winningUserProfile.getProfile().getEmail());
  }

  public EngineUserDto createKermitUserDtoWithEmail(final String email) {
    final UserProfileDto duplicateProfile2 = UserProfileDto.builder()
      .id(KERMIT_USER)
      .email(email)
      .build();
    return new EngineUserDto(duplicateProfile2, new UserCredentialsDto(KERMIT_USER));
  }

  private PlatformUserIdentityCache getSyncedIdentityCacheService() {
    return embeddedOptimizeExtension.getUserIdentityCache();
  }

}
