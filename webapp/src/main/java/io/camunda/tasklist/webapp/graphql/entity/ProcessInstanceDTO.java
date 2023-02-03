/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.entity;

public class ProcessInstanceDTO {

  private Long id;

  public Long getId() {
    return id;
  }

  public ProcessInstanceDTO setId(Long id) {
    this.id = id;
    return this;
  }
}
