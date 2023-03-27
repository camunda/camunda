/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.entity;

import java.util.Date;

public class DateFilterDTO {
  private Date from;
  private Date to;

  public Date getTo() {
    return to;
  }

  public DateFilterDTO setTo(Date to) {
    this.to = to;
    return this;
  }

  public Date getFrom() {
    return from;
  }

  public DateFilterDTO setFrom(Date from) {
    this.from = from;
    return this;
  }
}
