/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.UserTaskDbQuery;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskMigrationDbModel;
import java.util.List;

public interface UserTaskMapper extends ProcessBasedHistoryCleanupMapper {

  void insert(UserTaskDbModel taskDbModel);

  void insertCandidateUsers(UserTaskDbModel taskDbModel);

  void insertCandidateGroups(UserTaskDbModel taskDbModel);

  void update(UserTaskDbModel taskDbModel);

  void deleteCandidateUsers(Long key);

  void deleteCandidateGroups(Long key);

  void migrateToProcess(UserTaskMigrationDbModel dto);

  Long count(UserTaskDbQuery filter);

  List<UserTaskDbModel> search(UserTaskDbQuery filter);
}
