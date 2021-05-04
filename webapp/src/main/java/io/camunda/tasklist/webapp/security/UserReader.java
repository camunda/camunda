/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security;

import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import java.util.List;

public interface UserReader {

  public UserDTO getCurrentUser();

  public List<UserDTO> getUsersByUsernames(List<String> usernames);
}
