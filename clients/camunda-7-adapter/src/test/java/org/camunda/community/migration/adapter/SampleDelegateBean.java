/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.adapter;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("delegateBean")
public class SampleDelegateBean implements JavaDelegate {

  public static final Logger LOG = LoggerFactory.getLogger(SampleDelegateBean.class);

  public static boolean executed = false;
  public static String capturedVariable = null;
  public static String capturedBusinessKey = null;
  public static boolean canReachExecutionVariable = false;

  @Override
  public void execute(final DelegateExecution execution) {
    LOG.info("Called from process instance {}", execution.getProcessInstanceId());

    capturedVariable = (String) execution.getVariable("someVariable");
    canReachExecutionVariable = execution.getVariable("execution") != null;
    execution.setProcessBusinessKey("42");
    capturedBusinessKey = execution.getProcessBusinessKey();
    executed = true;
  }
}
