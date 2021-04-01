/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.security.sso;

import static io.zeebe.tasklist.util.CollectionUtil.map;
import static io.zeebe.tasklist.webapp.security.TasklistURIs.SSO_AUTH_PROFILE;

import com.auth0.jwt.interfaces.Claim;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.webapp.graphql.entity.UserDTO;
import io.zeebe.tasklist.webapp.security.UserReader;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Profile(SSO_AUTH_PROFILE)
public class SSOUserReader implements UserReader {

  private static final String EMPTY = "";

  @Autowired private TasklistProperties tasklistProperties;

  @Override
  public UserDTO getCurrentUser() {
    final SecurityContext context = SecurityContextHolder.getContext();
    final TokenAuthentication tokenAuth = (TokenAuthentication) context.getAuthentication();
    return buildUserDTOFrom(tokenAuth);
  }

  private UserDTO buildUserDTOFrom(TokenAuthentication tokenAuth) {
    final Map<String, Claim> claims = tokenAuth.getClaims();
    String name = "No name";
    if (claims.containsKey(tasklistProperties.getAuth0().getNameKey())) {
      name = claims.get(tasklistProperties.getAuth0().getNameKey()).asString();
    }
    return createUserDTO(name);
  }

  private UserDTO createUserDTO(String name) {
    return new UserDTO().setUsername(name).setFirstname(EMPTY).setLastname(name);
  }

  @Override
  public List<UserDTO> getUsersByUsernames(List<String> usernames) {
    // TODO #47
    return map(usernames, this::createUserDTO);
  }
}
