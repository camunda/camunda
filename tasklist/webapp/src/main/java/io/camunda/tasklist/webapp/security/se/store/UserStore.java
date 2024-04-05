/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.se.store;

import io.camunda.tasklist.entities.UserEntity;
import java.util.List;

public interface UserStore {
  UserEntity getByUserId(String userId);

  void create(UserEntity user);

  List<UserEntity> getUsersByUserIds(List<String> userIds);
}
