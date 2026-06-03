/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard.filter;

import io.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardProcessScopeFilterDataDto;

/**
 * Dashboard filter signalling that the dashboard exposes a process-scope filter — a process
 * ComboBox in the UI used to drill into a single process definition's metrics. Registered as the
 * {@code "processScope"} Jackson subtype on {@link DashboardFilterDto}.
 */
public class DashboardProcessScopeFilterDto
    extends DashboardFilterDto<DashboardProcessScopeFilterDataDto> {}
