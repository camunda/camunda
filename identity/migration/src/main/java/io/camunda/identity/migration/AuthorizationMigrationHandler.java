/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.migration;

import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.PatchAuthorizationRequest;
import io.camunda.service.UserServices;
import io.camunda.service.entities.UserEntity;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionAction;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationMigrationHandler {

  private AuthorizationServices authorizationService;
  private UserServices userService;

  public void migrate() {
    // start loop
    // call identity to get authorization
    // group by owner, resourceType
    // then each group grouped by permission (list of resource ids)
    // save authorizations
    final var userQuery =
        SearchQueryBuilders.userSearchQuery(
            fn -> fn.filter(f -> f.username("")).page(p -> p.size(1)));

    final List<UserEntity> users = userService.search(userQuery).items();
    var userKey = 0L;
    if (users.isEmpty()) {
      userKey = userService.createUser(username, name, email, "").get();
    } else {
      userKey = users.get(0).key();
    }

    authorizationService.createAuthorization(
        new PatchAuthorizationRequest(
            userKey,
            PermissionAction.ADD,
            AuthorizationResourceType.AUTHORIZATION,
            Map.of(PermissionType.CREATE, Arrays.asList(""))));
    // call identity to mark resource authorization as migrated
  }
}
