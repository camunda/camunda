/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetDto;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

public interface BusinessValueTargetRepository {

  String ID_SEPARATOR = "::";

  void upsert(BusinessValueTargetDto target);

  Optional<BusinessValueTargetDto> getByKey(String tenantId, String processDefinitionKey);

  List<BusinessValueTargetDto> scanAll();

  /**
   * Reads the targets belonging to the given tenants.
   *
   * <p>Passing {@code null} returns every target and is reserved for internal, tenant-agnostic
   * callers. Passing an empty collection returns no targets — a shortcut for callers that have
   * already determined the caller sees no tenants. Any non-empty collection is pushed down to a
   * {@code terms} filter so a request path never pulls another tenant's rows back to filter them in
   * memory, and so the fetch limit bounds what this caller can see rather than the whole fleet.
   */
  List<BusinessValueTargetDto> readByTenants(Collection<String> tenantIds);

  static String documentId(final String tenantId, final String processDefinitionKey) {
    if (StringUtils.isBlank(tenantId)) {
      throw new IllegalArgumentException(
          "tenantId must not be null or blank on a business-value target");
    }
    if (StringUtils.isBlank(processDefinitionKey)) {
      throw new IllegalArgumentException(
          "processDefinitionKey must not be null or blank on a business-value target");
    }
    return tenantId + ID_SEPARATOR + processDefinitionKey;
  }
}
