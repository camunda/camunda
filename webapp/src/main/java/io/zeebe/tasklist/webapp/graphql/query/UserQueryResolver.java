/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.graphql.query;

import graphql.kickstart.tools.GraphQLQueryResolver;
import io.zeebe.tasklist.webapp.graphql.entity.UserDTO;
import org.springframework.stereotype.Component;

@Component
public final class UserQueryResolver implements GraphQLQueryResolver {

  // TODO #45
  public UserDTO currentUser() {
    final UserDTO user = new UserDTO();
    user.setUsername("demo");
    user.setFirstname("Demo");
    user.setLastname("User");
    return user;
  }
}
