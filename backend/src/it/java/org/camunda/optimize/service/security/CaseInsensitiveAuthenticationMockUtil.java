/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security;

import com.google.common.collect.Lists;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.engine.AuthenticationResultDto;
import org.camunda.optimize.dto.engine.EngineListUserDto;
import org.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtension;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CaseInsensitiveAuthenticationMockUtil {

  public static List<HttpRequest> setupCaseInsensitiveAuthentication(final EmbeddedOptimizeExtension embeddedOptimizeExtension,
                                                                     final EngineIntegrationExtension engineIntegrationExtension,
                                                                     final ClientAndServer engineMockServer,
                                                                     final String allUpperCaseUserId,
                                                                     final String actualUserId) {
    // a case-insensitive authentication backend (e.g. LDAP)
    final HttpRequest authenticateRequestWithUppercaseUserId = HttpRequest.request()
      .withPath(engineIntegrationExtension.getEnginePath() + "/identity/verify")
      .withBody(embeddedOptimizeExtension.toJsonString(new CredentialsRequestDto(allUpperCaseUserId, actualUserId)));
    engineMockServer
      .when(authenticateRequestWithUppercaseUserId)
      .respond(
        HttpResponse
          .response()
          .withBody(
            // engine API returns userId with same case as passed in
            embeddedOptimizeExtension.toJsonString(
              AuthenticationResultDto.builder().authenticatedUser(allUpperCaseUserId).isAuthenticated(true).build()
            ),
            MediaType.APPLICATION_JSON
          )
      );

    // and case-insensitive user by id retrieval from the same backend
    final HttpRequest getUserByUppercaseIdRequest = HttpRequest.request()
      .withPath(engineIntegrationExtension.getEnginePath() + "/user/" + allUpperCaseUserId + "/profile");
    engineMockServer
      .when(getUserByUppercaseIdRequest)
      .respond(
        HttpResponse
          .response()
          .withBody(
            // here the correct case userId should get returned
            embeddedOptimizeExtension.toJsonString(EngineListUserDto.builder().id(actualUserId).build()),
            MediaType.APPLICATION_JSON
          ));

    return Lists.newArrayList(authenticateRequestWithUppercaseUserId, getUserByUppercaseIdRequest);
  }
}
