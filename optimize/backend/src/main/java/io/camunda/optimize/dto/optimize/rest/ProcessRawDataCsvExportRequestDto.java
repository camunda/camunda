/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Builder
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessRawDataCsvExportRequestDto {
  @NotNull private String processDefinitionKey;
  @NotEmpty @Builder.Default private List<String> processDefinitionVersions = new ArrayList<>();
  @NotNull @Builder.Default private List<String> tenantIds = Collections.singletonList(null);
  @Builder.Default @NotNull private List<ProcessFilterDto<?>> filter = new ArrayList<>();
  @NotEmpty @Builder.Default private List<String> includedColumns = new ArrayList<>();
}
