/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.service.AbstractMultiEngineIT;
import org.camunda.optimize.service.util.importing.EngineConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpError;
import org.slf4j.event.Level;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.once;

public class MultiEnginePlatformIdentityServiceIT extends AbstractMultiEngineIT {

  @RegisterExtension
  protected final LogCapturer logCapturer = LogCapturer.create()
    .forLevel(Level.WARN)
    .captureForType(PlatformIdentityService.class);

  @Test
  public void fetchUserFromConnectedEngineWhenOtherEnginesAreDown() {
    // given
    addSecondEngineToConfiguration();

    defaultEngineAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    final String otherEngineUser = "otherUser";
    secondaryEngineAuthorizationClient.addUserAndGrantOptimizeAccess(otherEngineUser);

    // make sure there are no cached entries
    embeddedOptimizeExtension.getUserIdentityCache().resetCache();

    final ClientAndServer firstEngineMock = useAndGetEngineMockServer();
    // requests for otherUser to the first engine will fail hard
    firstEngineMock.when(
      request().withPath(engineIntegrationExtension.getEnginePath() + createGetUserByIdPath(otherEngineUser))
    ).error(HttpError.error().withDropConnection(true));
    final ClientAndServer secondEngineMock = useAndGetSecondaryEngineMockServer();

    // when
    final Optional<UserDto> userById = getIdentityService().getUserById(otherEngineUser);

    // then
    assertThat(userById).isPresent().get().extracting(IdentityDto::getId).isEqualTo(otherEngineUser);
    // first engine was called (but failed)
    firstEngineMock.verify(
      request().withPath(engineIntegrationExtension.getEnginePath() + createGetUserByIdPath(otherEngineUser)),
      atLeast(1)
    );
    // which was logged
    logCapturer.assertContains(String.format(
      "Failed fetching identity with id %s from engine %s.", otherEngineUser, DEFAULT_ENGINE_ALIAS
    ));
    // second engine was called to return the user
    secondEngineMock.verify(
      request().withPath(secondaryEngineIntegrationExtension.getEnginePath() + createGetUserByIdPath(otherEngineUser)),
      once()
    );
  }

  private String createGetUserByIdPath(final String userId) {
    return EngineConstants.USER_BY_ID_ENDPOINT_TEMPLATE.replace("{id}", userId);
  }

  private PlatformIdentityService getIdentityService() {
    return embeddedOptimizeExtension.getIdentityService();
  }

}
