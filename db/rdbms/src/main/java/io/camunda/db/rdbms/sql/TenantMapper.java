/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.TenantDbQuery;
import io.camunda.db.rdbms.read.domain.TenantMemberDbQuery;
import io.camunda.db.rdbms.write.domain.TenantDbModel;
import io.camunda.db.rdbms.write.domain.TenantMemberDbModel;
import java.util.List;

public interface TenantMapper {

  void insert(TenantDbModel tenant);

  void update(TenantDbModel tenant);

  void delete(Long tenantKey);

  void insertMember(TenantMemberDbModel member);

  void deleteMember(TenantMemberDbModel member);

  void deleteAllMembers(Long tenantKey);

  Long count(TenantDbQuery filter);

  List<TenantDbModel> search(TenantDbQuery filter);

  Long countMembers(TenantMemberDbQuery filter);

  List<TenantMemberDbModel> searchMembers(TenantMemberDbQuery filter);
}
