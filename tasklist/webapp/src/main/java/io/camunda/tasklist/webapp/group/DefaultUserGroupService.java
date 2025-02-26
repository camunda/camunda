/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.group;

import static io.camunda.tasklist.property.IdentityProperties.FULL_GROUP_ACCESS;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!consolidated-auth")

/**
 * Fallback component for `UserGroupService`, used when the "consolidated-auth" profile is **not
 * active**.
 *
 * <p>- This component ensures that authorization is always available, even when consolidated
 * authentication is not enabled. - It provides a **default, full-access authorization service**.
 *
 * <p>This component declaration can be removed after the consolidated authentication is fully
 * implemented
 */
public class DefaultUserGroupService implements UserGroupService {
  @Override
  public List<String> getUserGroups() {
    final List<String> defaultGroups = new ArrayList<>();
    defaultGroups.add(FULL_GROUP_ACCESS);
    return defaultGroups;
  }
}
