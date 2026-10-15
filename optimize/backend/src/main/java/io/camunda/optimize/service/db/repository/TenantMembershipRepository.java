/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import io.camunda.optimize.dto.optimize.query.TenantMembershipDto;
import io.camunda.optimize.dto.optimize.query.TenantMembershipDto.MemberType;
import java.util.List;
import java.util.Set;

/**
 * POC: storage port for tenant assignments, implemented per database (Elasticsearch/OpenSearch).
 */
public interface TenantMembershipRepository {

  /** Idempotent: assigning an existing member replaces the identical document. */
  void assign(TenantMembershipDto membership);

  /** A no-op when the assignment does not exist. */
  void unassign(String tenantId, MemberType memberType, String memberId);

  /** Tenant ids assigned to any of the given principals, in a single lookup. */
  Set<String> findTenantIds(MemberType memberType, Set<String> memberIds);

  List<TenantMembershipDto> findByTenant(String tenantId);
}
