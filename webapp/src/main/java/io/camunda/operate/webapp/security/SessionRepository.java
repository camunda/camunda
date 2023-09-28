/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security;

import java.util.List;
import java.util.Optional;

public interface SessionRepository {
  List<String> getExpiredSessionIds();

  void save(OperateSession session);

  Optional<OperateSession> findById(final String id);

  void deleteById(String id);
}
