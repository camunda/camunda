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

  /**
   * Returns every document that carries a target, across all tenants. Scrolled rather than capped:
   * the overview sweep decides what to measure from this list, so a truncated result would silently
   * leave targeted definitions unmeasured.
   *
   * <p>Cleared targets are excluded. Clearing rewrites the document with every target field null
   * rather than deleting it, and such a document carries nothing the sweep can measure or apply.
   */
  List<BusinessValueTargetDto> scanAll();

  /**
   * Reads the targets belonging to the given tenants. Like {@link #scanAll()}, cleared targets are
   * excluded, so they do not count towards the fetch limit.
   *
   * <p>Passing {@code null} returns every target and is reserved for internal, tenant-agnostic
   * callers. Passing an empty collection returns no targets — a shortcut for callers that have
   * already determined the caller sees no tenants. Any non-empty collection is pushed down to a
   * {@code terms} filter so a request path never pulls another tenant's rows back to filter them in
   * memory, and so the fetch limit bounds what this caller can see rather than the whole fleet.
   */
  List<BusinessValueTargetDto> readByTenants(Collection<String> tenantIds);

  /**
   * Deletes the target documents with the given ids in a single bulk request, as built by {@link
   * #documentId(String, String)}. A null or empty collection issues no request, matching {@code
   * bulkUpsert}.
   *
   * <p>The list is expected to be bounded by the caller, so no chunking happens here, mirroring
   * {@code ProcessInstanceRepository#deleteByIds}.
   */
  void deleteByIds(List<String> documentIds);

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
