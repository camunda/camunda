/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.se.store;

import io.camunda.tasklist.v86.entities.UserEntity;
import java.util.List;

public interface UserStore {
  UserEntity getByUserId(String userId);

  void create(UserEntity user);

  List<UserEntity> getUsersByUserIds(List<String> userIds);
}
