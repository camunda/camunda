/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.UserDbQuery;
import io.camunda.db.rdbms.write.domain.UserDbModel;
import io.camunda.search.entities.UserEntity;
import java.util.List;

public interface UserMapper {

  void insert(UserDbModel processDeployment);

  Long count(UserDbQuery filter);

  List<UserEntity> search(UserDbQuery filter);
}