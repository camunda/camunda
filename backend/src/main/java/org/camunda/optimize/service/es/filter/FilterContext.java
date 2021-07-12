/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import lombok.Builder;
import lombok.Value;

import java.time.ZoneId;

@Builder
@Value
public class FilterContext {
  ZoneId timezone;
  boolean userTaskReport;
}
