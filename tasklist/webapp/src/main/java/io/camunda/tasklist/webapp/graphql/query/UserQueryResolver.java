/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.query;

import graphql.kickstart.tools.GraphQLQueryResolver;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.UserReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class UserQueryResolver implements GraphQLQueryResolver {

  @Autowired private UserReader userReader;

  public UserDTO currentUser() {
    return userReader.getCurrentUser();
  }
}
