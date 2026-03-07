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
import java.util.List;
import java.util.Optional;

/** Read-only port for tenant lookups. */
public interface TenantReadPort {
  Optional<AuthTenant> findById(String tenantId);

  List<AuthTenant> findByMember(String memberId, MemberType memberType);
}
