/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import java.util.List;

public interface ApplicationAuthorizationService {

  boolean isUserAuthorizedToAccessOptimize(String userId);

  boolean isGroupAuthorizedToAccessOptimize(String groupId);

  List<String> getAuthorizedEnginesForUser(String userId);

  List<String> getAuthorizedEnginesForGroup(String groupId);

}
