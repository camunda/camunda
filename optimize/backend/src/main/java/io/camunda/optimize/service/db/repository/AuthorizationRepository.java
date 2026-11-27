/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import io.camunda.optimize.dto.optimize.access.AuthorizationDto;
import java.util.List;
import java.util.Optional;

/**
 * Authorizations in the {@code authorization} index. Writes refresh the index, so a change is
 * visible to the next access check.
 */
public interface AuthorizationRepository {

  Optional<AuthorizationDto> get(String id);

  List<AuthorizationDto> getAll();

  /** Inserts the authorization, or replaces it when one with the same id exists. */
  void upsert(AuthorizationDto authorization);

  /** Deletes the authorization; a no-op when it does not exist. */
  void delete(String id);
}
