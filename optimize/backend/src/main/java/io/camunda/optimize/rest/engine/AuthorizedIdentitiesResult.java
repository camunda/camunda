/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.engine;

import java.util.HashSet;
import java.util.Set;
import lombok.Data;

@Data
public class AuthorizedIdentitiesResult {
  boolean globalOptimizeGrant = false;
  final Set<String> grantedGroupIds = new HashSet<>();
  final Set<String> revokedGroupIds = new HashSet<>();
  final Set<String> grantedUserIds = new HashSet<>();
  final Set<String> revokedUserIds = new HashSet<>();
}
