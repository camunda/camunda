/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.port.outbound;

import io.camunda.auth.domain.model.AuthRole;
import io.camunda.auth.domain.model.MemberType;

/** Write port for role persistence. Only available in standalone persistence mode. */
public interface RoleWritePort {
  void save(AuthRole role);

  void deleteById(String roleId);

  void addMember(String roleId, String memberId, MemberType memberType);

  void removeMember(String roleId, String memberId, MemberType memberType);
}
