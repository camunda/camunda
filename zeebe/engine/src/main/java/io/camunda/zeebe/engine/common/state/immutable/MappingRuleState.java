/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.immutable;

import io.camunda.zeebe.engine.common.state.authorization.PersistedMappingRule;
import java.util.Collection;
import java.util.Optional;

public interface MappingRuleState {

  Optional<PersistedMappingRule> get(final String mappingRuleId);

  Optional<PersistedMappingRule> get(final String claimName, final String claimValue);

  Collection<PersistedMappingRule> getAll();
}
