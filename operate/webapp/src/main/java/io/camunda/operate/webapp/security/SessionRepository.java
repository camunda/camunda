/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security;

import java.util.List;
import java.util.Optional;

public interface SessionRepository {

  String POLLING_HEADER = "x-is-polling";

  List<String> getExpiredSessionIds();

  void save(OperateSession session);

  Optional<OperateSession> findById(final String id);

  void deleteById(String id);
}
