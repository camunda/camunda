/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util.j5templates;

import io.camunda.operate.property.OperateProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;

@Component
@ConditionalOnProperty(prefix = OperateProperties.PREFIX, name = "webappEnabled", havingValue = "true", matchIfMissing = true)
public class SearchCheckPredicatesHolder {
  @Autowired
  @Qualifier("processIsDeployedCheck")
  private Predicate<Object[]> processIsDeployedCheck;

  @Autowired
  @Qualifier("processInstanceExistsCheck")
  private Predicate<Object[]> processInstanceExistsCheck;

  @Autowired
  @Qualifier("flowNodeIsCompletedCheck")
  private Predicate<Object[]> flowNodeIsCompletedCheck;

  @Autowired
  @Qualifier("processInstancesAreFinishedCheck")
  private Predicate<Object[]> processInstancesAreFinishedCheck;

  public Predicate<Object[]> getProcessIsDeployedCheck() {
    return processIsDeployedCheck;
  }

  public Predicate<Object[]> getProcessInstanceExistsCheck() {
    return processInstanceExistsCheck;
  }

  public Predicate<Object[]> getFlowNodeIsCompletedCheck() {
    return flowNodeIsCompletedCheck;
  }

  public Predicate<Object[]> getProcessInstancesAreFinishedCheck() {
    return processInstancesAreFinishedCheck;
  }
}
