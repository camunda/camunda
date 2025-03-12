/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.adapter;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("sampleBean")
public class SampleBean {
  public static final Logger LOG = LoggerFactory.getLogger(SampleBean.class);
  public static boolean executionReceived = false;
  public static boolean someVariableReceived = false;

  public void doStuff(final DelegateExecution execution, final String someVariable) {
    executionReceived = execution != null;
    someVariableReceived = someVariable != null;
    LOG.info("Called from process instance {}", execution.getProcessInstanceId());
  }
}
