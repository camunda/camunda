/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.oauth;

import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;

import io.camunda.identity.sdk.Identity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@Profile(IDENTITY_AUTH_PROFILE)
public class IdentityJwt2AuthenticationTokenConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  @Autowired private Identity identity;

  @Override
  public AbstractAuthenticationToken convert(final Jwt jwt) {
    // this will validate audience
    identity.authentication().verifyToken(jwt.getTokenValue());
    return new JwtAuthenticationToken(jwt, null, jwt.getSubject());
  }
}
