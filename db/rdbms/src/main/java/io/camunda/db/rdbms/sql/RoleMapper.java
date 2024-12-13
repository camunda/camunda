/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.RoleDbQuery;
import io.camunda.db.rdbms.write.domain.RoleDbModel;
import io.camunda.db.rdbms.write.domain.RoleMemberDbModel;
import java.util.List;

public interface RoleMapper {

  void insert(RoleDbModel role);

  void update(RoleDbModel role);

  void delete(Long roleKey);

  void insertMember(RoleMemberDbModel member);

  void deleteMember(RoleMemberDbModel member);

  void deleteAllMembers(Long roleKey);

  Long count(RoleDbQuery filter);

  List<RoleDbModel> search(RoleDbQuery filter);
}
