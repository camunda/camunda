/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.transformers;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.zeebe.protocol.clientapi.ValueType;

@Configuration
public class TransfromerHolder {

  @Autowired
  private ApplicationContext applicationContext;
  @Autowired
  private WorkflowInstanceRecordTransformer workflowInstanceRecordTransformer;
  @Autowired
  private DeploymentEventTransformer deploymentEventTransformer;
  @Autowired
  private JobEventTransformer jobEventTransformer;
  @Autowired
  private IncidentEventTransformer incidentEventTransformer;

  @Bean
  public Map<ValueType, AbstractRecordTransformer> getZeebeRecordTransformerMapping() {
    Map<ValueType, AbstractRecordTransformer> map = new HashMap<>();
    map.put(ValueType.WORKFLOW_INSTANCE, workflowInstanceRecordTransformer);
    map.put(ValueType.DEPLOYMENT, deploymentEventTransformer);
    map.put(ValueType.JOB, jobEventTransformer);
    map.put(ValueType.INCIDENT, incidentEventTransformer);
    return map;
  }

}
