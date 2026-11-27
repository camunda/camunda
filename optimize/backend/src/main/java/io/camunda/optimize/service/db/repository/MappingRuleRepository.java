/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import io.camunda.optimize.dto.optimize.access.MappingRuleDto;
import java.util.List;
import java.util.Optional;

/**
 * Mapping rules in the {@code mapping-rule} index. Writes refresh the index, so a change is visible
 * to the next access check.
 */
public interface MappingRuleRepository {

  Optional<MappingRuleDto> get(String mappingRuleId);

  /** Returns every mapping rule, as CSL matches the token claims against all of them. */
  List<MappingRuleDto> getAll();

  /** Inserts the mapping rule, or replaces it when one with the same id exists. */
  void upsert(MappingRuleDto mappingRule);

  /** Deletes the mapping rule; a no-op when it does not exist. */
  void delete(String mappingRuleId);
}
