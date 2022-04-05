/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.oauth2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class Jwt2AuthenticationTokenConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  private final JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();

  @Autowired
  private JwtAuthenticationTokenValidator validator;

  @Override
  public AbstractAuthenticationToken convert(final Jwt jwt) {
    final JwtAuthenticationToken token = (JwtAuthenticationToken) delegate.convert(jwt);
    if (validator.isValid(token)) {
      return token;
    }
    return null;
  }

}
