/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security;

public interface SessionListener {
  void onSessionCreate(String userId);
  void onSessionRefresh(String userId);
  void onSessionDestroy(String userId);
}
