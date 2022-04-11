/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.engine;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class AuthorizedIdentitiesResult {
  boolean globalOptimizeGrant = false;
  final Set<String> grantedGroupIds = new HashSet<>();
  final Set<String> revokedGroupIds = new HashSet<>();
  final Set<String> grantedUserIds = new HashSet<>();
  final Set<String> revokedUserIds = new HashSet<>();
}
