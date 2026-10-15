/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import io.camunda.optimize.dto.optimize.query.AccessMappingRuleDto;
import io.camunda.optimize.dto.optimize.query.AccessRoleDto;
import java.util.List;

/**
 * POC: storage port for the mapping rules and roles that decide who may use Optimize, replacing
 * what Management Identity held for it.
 */
public interface AccessRepository {

  List<AccessMappingRuleDto> findAllMappingRules();

  List<AccessRoleDto> findAllRoles();

  /**
   * Returns {@code true} when the rule was inserted, {@code false} when one with its id existed.
   */
  boolean createMappingRuleIfMissing(AccessMappingRuleDto rule);

  /**
   * Returns {@code true} when the role was inserted, {@code false} when one with its id existed.
   */
  boolean createRoleIfMissing(AccessRoleDto role);

  /** Inserts or replaces the rule. */
  void saveMappingRule(AccessMappingRuleDto rule);

  /** Inserts or replaces the role. */
  void saveRole(AccessRoleDto role);

  void deleteMappingRule(String id);

  void deleteRole(String id);
}
