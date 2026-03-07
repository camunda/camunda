/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.port.outbound;

import io.camunda.auth.domain.model.AuthTenant;
import io.camunda.auth.domain.model.MemberType;

/** Write port for tenant persistence. Only available in standalone persistence mode. */
public interface TenantWritePort {
  void save(AuthTenant tenant);
  void deleteById(String tenantId);
  void addMember(String tenantId, String memberId, MemberType memberType);
  void removeMember(String tenantId, String memberId, MemberType memberType);
}
