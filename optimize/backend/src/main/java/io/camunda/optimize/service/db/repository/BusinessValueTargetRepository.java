/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetDto;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

public interface BusinessValueTargetRepository {

  String ID_SEPARATOR = "::";

  void upsert(BusinessValueTargetDto target);

  Optional<BusinessValueTargetDto> getByKey(String tenantId, String processDefinitionKey);

  List<BusinessValueTargetDto> scanAll();

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
