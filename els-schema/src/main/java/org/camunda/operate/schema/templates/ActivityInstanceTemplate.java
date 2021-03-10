/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.schema.templates;

import org.springframework.stereotype.Component;

@Component
public class ActivityInstanceTemplate extends AbstractTemplateDescriptor implements WorkflowInstanceDependant {

  public static final String INDEX_NAME = "activity-instance";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String POSITION = "position";
  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String ACTIVITY_ID = "activityId";
  public static final String INCIDENT_KEY = "incidentKey";
  public static final String STATE = "state";
  public static final String TYPE = "type";
  public static final String SCOPE_KEY = "scopeKey";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }


}
