/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report;

/**
 * Is used to check if two single reports can be combined with each other
 * to a combined report.
 */
public interface Combinable {

  boolean isCombinable(Object o);
}
