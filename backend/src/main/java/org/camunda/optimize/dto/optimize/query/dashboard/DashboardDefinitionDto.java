/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.dashboard;

import lombok.Data;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DashboardDefinitionDto extends BaseDashboardDefinitionDto implements CollectionEntity {

  protected List<ReportLocationDto> reports = new ArrayList<>();
}
