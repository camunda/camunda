/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.GroupDbQuery;
import io.camunda.db.rdbms.write.domain.GroupDbModel;
import io.camunda.db.rdbms.write.domain.GroupMemberDbModel;
import java.util.List;

public interface GroupMapper {

  void insert(GroupDbModel group);

  void update(GroupDbModel group);

  void delete(String groupId);

  void insertMember(GroupMemberDbModel member);

  void deleteMember(GroupMemberDbModel member);

  void deleteAllMembers(String groupId);

  Long count(GroupDbQuery filter);

  List<GroupDbModel> search(GroupDbQuery filter);
}
