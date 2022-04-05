/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.rest.dto.incidents;

import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByErrorMsgStatisticsDto;
import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class IncidentsByErrorMsgStatisticsTest {

  @Test
  public void testCompareInstancesWithErrorCounts() {
    IncidentsByErrorMsgStatisticsDto first = new IncidentsByErrorMsgStatisticsDto("an error");
    first.setInstancesWithErrorCount(5);
    IncidentsByErrorMsgStatisticsDto second = new IncidentsByErrorMsgStatisticsDto("an error");
    first.setInstancesWithErrorCount(3);
    assertThat(IncidentsByErrorMsgStatisticsDto.COMPARATOR.compare(first, second)).isLessThan(0);
  }

}
