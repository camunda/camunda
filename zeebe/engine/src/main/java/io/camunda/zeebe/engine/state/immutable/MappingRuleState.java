/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.authorization.PersistedMappingRule;
import java.util.Collection;
import java.util.Optional;

public interface MappingRuleState {

  /** Returns the mapping rule visible to a command outside of a policy update. */
  default Optional<PersistedMappingRule> get(final String mappingRuleId) {
    return get(mappingRuleId, 0);
  }

  /**
   * Returns the mapping rule visible to a command of the given policy version. The command sees the
   * current version and its own version, so a policy update reads its own writes.
   */
  Optional<PersistedMappingRule> get(String mappingRuleId, long commandVersion);

  default Optional<PersistedMappingRule> get(final String claimName, final String claimValue) {
    return get(claimName, claimValue, 0);
  }

  Optional<PersistedMappingRule> get(String claimName, String claimValue, long commandVersion);

  Collection<PersistedMappingRule> getAll();
}
