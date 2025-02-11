/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.rest.dto.incidents;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByErrorMsgStatisticsDto;
import org.junit.jupiter.api.Test;

public class IncidentsByErrorMsgStatisticsTest {

  @Test
  public void testCompareInstancesWithErrorCounts() {
    final String errorMessage = "an error";
    final Integer errorMessageHash = errorMessage.hashCode();
    final IncidentsByErrorMsgStatisticsDto first =
        new IncidentsByErrorMsgStatisticsDto(errorMessage, errorMessageHash);
    first.setInstancesWithErrorCount(5);
    final IncidentsByErrorMsgStatisticsDto second =
        new IncidentsByErrorMsgStatisticsDto(errorMessage, errorMessageHash);
    first.setInstancesWithErrorCount(3);
    assertThat(IncidentsByErrorMsgStatisticsDto.COMPARATOR.compare(first, second)).isLessThan(0);
  }
}
