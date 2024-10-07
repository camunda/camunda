/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.migration;

import io.camunda.identity.migration.dto.UserResourceAuthorization;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ManagementIdentityProxy {

  private final RestTemplate restTemplate = new RestTemplate();

  public List<UserResourceAuthorization> fetchUserResourceAuthorizations(
      final UserResourceAuthorization lastRecord, final int pageSize) {
    return new ArrayList<>();
  }

  public void markAsMigrated(final Collection<UserResourceAuthorization> migrated) {}
}
