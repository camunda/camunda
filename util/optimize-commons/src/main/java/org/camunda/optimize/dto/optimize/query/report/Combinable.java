/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report;

/**
 * Is used to check if two single reports can be combined with each other
 * to a combined report.
 */
public interface Combinable {

  boolean isCombinable(Object o);

  static <O extends Combinable> boolean isCombinable(O o1, O o2) {
    if (o1 == null && o2 == null) {
      return true;
    } else if (o1 == null) {
      return false;
    } else if (o2 == null) {
      return false;
    } else {
      return o1.isCombinable(o2);
    }
  }
}
