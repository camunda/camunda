/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.filter.UsageMetricsFilter;
import java.util.List;

/**
 * @param filter the search filter
 * @param authorizedTenantIds the tenants the caller may see; empty means the tenant check is
 *     disabled, as the reader returns an empty result before querying when the check is enabled
 *     without tenants
 */
public record UsageMetricsDbQuery(UsageMetricsFilter filter, List<String> authorizedTenantIds) {}
