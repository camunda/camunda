/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.webapp.api.v1.dao.FlowNodeStatisticsDao;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeStatistics;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchFlowNodeStatisticsDao implements FlowNodeStatisticsDao {
  @Override
  public List<FlowNodeStatistics> getFlowNodeStatisticsForProcessInstance(Long processInstanceKey) {
    throw new UnsupportedOperationException();
  }
}
