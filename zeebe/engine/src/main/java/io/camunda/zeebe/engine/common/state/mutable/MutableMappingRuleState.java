/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.mutable;

import io.camunda.zeebe.engine.common.state.immutable.MappingRuleState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;

public interface MutableMappingRuleState extends MappingRuleState {

  void create(final MappingRuleRecord mappingRuleRecord);

  void update(MappingRuleRecord mappingRuleRecord);

  void delete(final String id);
}
