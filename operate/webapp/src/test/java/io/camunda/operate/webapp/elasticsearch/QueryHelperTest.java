/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.operate.webapp.security.permission.PermissionsService.ResourcesAllowed;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import org.junit.jupiter.api.Test;

public class QueryHelperTest {

  @Test
  public void shouldUseIncidentFieldWhenActivityIdAndIncidentsAreSet() {
    final QueryHelper queryHelper = new QueryHelper();
    final String activityId = "testActivityId";

    final PermissionsService permissionsService = mock(PermissionsService.class);
    final ListViewQueryDto queryDto =
        new ListViewQueryDto().setActivityId(activityId).setIncidents(true).setRunning(true);

    queryHelper.setPermissionsService(permissionsService);
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(ResourcesAllowed.wildcard());

    final var qb = queryHelper.createQueryFragment(queryDto);
    assertThat(qb).as("QueryBuilder should not be null").isNotNull();
    final String queryString = qb.toString();
    assertThat(queryString).as("Should contain activityState field").contains("activityState");
    assertThat(queryString).as("Should contain activityState=ACTIVE").contains("ACTIVE");
    assertThat(queryString).as("Should contain activityId field").contains("activityId");
    assertThat(queryString).as("Should contain the provided activityId").contains(activityId);
    assertThat(queryString).as("Should contain incident field").contains("incident");
    assertThat(queryString).as("Should contain incident=true").contains("true");
    // Ensure error message field is not used
    assertThat(queryString)
        .as("Should not contain errorMessage field")
        .doesNotContain("errorMessage");
  }
}
