/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.j5templates;

import io.camunda.config.operate.OperateProperties;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = OperateProperties.PREFIX,
    name = "webappEnabled",
    havingValue = "true",
    matchIfMissing = true)
public class SearchCheckPredicatesHolder {
  @Autowired
  @Qualifier("processInstancesAreStartedCheck")
  private Predicate<Object[]> processInstancesAreStartedCheck;

  @Autowired
  @Qualifier("flowNodeIsActiveCheck")
  private Predicate<Object[]> flowNodeIsActiveCheck;

  // Note: The operation check predicates check for the same status for *all* operations for
  // a process instance, not a single one. This causes issues in tests where we want mixed
  // operation statuses (ex: 1 completed 1 failed operation) and should be used sparingly
  // and deliberately until a better solution is found.
  @Autowired
  @Qualifier("operationsByProcessInstanceAreCompletedCheck")
  private Predicate<Object[]> operationsByProcessInstanceAreCompletedCheck;

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

  @Autowired
  @Qualifier("incidentsAreActiveCheck")
  private Predicate<Object[]> incidentsAreActiveCheck;

  @Autowired
  @Qualifier("incidentsInProcessAreActiveCheck")
  private Predicate<Object[]> incidentsInProcessAreActiveCheck;

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

  public Predicate<Object[]> getIncidentsAreActiveCheck() {
    return incidentsAreActiveCheck;
  }

  public Predicate<Object[]> getIncidentsInProcessAreActiveCheck() {
    return incidentsInProcessAreActiveCheck;
  }

  public Predicate<Object[]> getOperationsByProcessInstanceAreCompletedCheck() {
    return operationsByProcessInstanceAreCompletedCheck;
  }

  public Predicate<Object[]> getFlowNodeIsActiveCheck() {
    return flowNodeIsActiveCheck;
  }

  public Predicate<Object[]> getProcessInstancesAreStartedCheck() {
    return processInstancesAreStartedCheck;
  }
}
