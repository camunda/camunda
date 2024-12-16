/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.AuthorizationDbQuery;
import io.camunda.db.rdbms.write.domain.AuthorizationDbModel;
import java.util.List;

public interface AuthorizationMapper {

  void insert(AuthorizationDbModel authorization);

  void delete(AuthorizationDbModel authorizationDbModel);

  void insertPermissions(AuthorizationDbModel authorization);

  void insertPermissionResourceIds(AuthorizationDbModel authorization);

  void deletePermissionResourceIds(AuthorizationDbModel authorization);

  void deleteAllMembers(Long authorizationKey);

  Long count(AuthorizationDbQuery filter);

  List<AuthorizationDbModel> search(AuthorizationDbQuery filter);
}
