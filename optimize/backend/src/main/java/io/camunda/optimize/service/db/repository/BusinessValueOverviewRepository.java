/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

public interface BusinessValueOverviewRepository {

  String ID_SEPARATOR = "::";

  void bulkUpsert(List<BusinessValueOverviewDto> rows, boolean refreshImmediately);

  Optional<BusinessValueOverviewDto> getByKey(
      String tenantId, String processDefinitionKey, MetricRange metricRange);

  List<BusinessValueOverviewDto> readByRange(MetricRange metricRange);

  static String documentId(
      final String tenantId, final String processDefinitionKey, final MetricRange metricRange) {
    if (StringUtils.isBlank(tenantId)) {
      throw new IllegalArgumentException(
          "tenantId must not be null or blank on a business-value overview row");
    }
    if (StringUtils.isBlank(processDefinitionKey)) {
      throw new IllegalArgumentException(
          "processDefinitionKey must not be null or blank on a business-value overview row");
    }
    if (metricRange == null) {
      throw new IllegalArgumentException(
          "metricRange must not be null on a business-value overview row");
    }
    return tenantId + ID_SEPARATOR + processDefinitionKey + ID_SEPARATOR + metricRange.getId();
  }
}
