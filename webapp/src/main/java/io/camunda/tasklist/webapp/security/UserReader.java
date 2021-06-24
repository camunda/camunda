/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security;

import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import java.util.List;

public interface UserReader {

  String DEFAULT_ORGANIZATION = "null";
  String EMPTY = "";

  String DEFAULT_USER = "No name";

  UserDTO getCurrentUser();

  String getCurrentUserId();

  String getCurrentOrganizationId();

  List<UserDTO> getUsersByUsernames(List<String> usernames);
}
