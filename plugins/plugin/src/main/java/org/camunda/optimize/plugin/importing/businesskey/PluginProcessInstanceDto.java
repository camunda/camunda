/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.plugin.importing.businesskey;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Optional;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class PluginProcessInstanceDto {

  /**
   * The ID of the process instance.
   */
  protected String id;

  /**
   * The Business Key of the process instance.
   */
  protected String businessKey;

  /**
   * The ID of the process definition this instance corresponds to.
   */
  protected String processDefinitionId;

  /**
   * The key of the process definition this instance corresponds to.
   */
  protected String processDefinitionKey;

  /**
   * The version of the process definition this process instance corresponds to.
   */
  protected String processDefinitionVersion;

  /**
   * The name of the process definition this process instance corresponds to.
   */
  protected String processDefinitionName;

  /**
   * The start time of the process instance.
   */
  protected OffsetDateTime startTime;

  /**
   * The end time of the process instance.
   */
  protected OffsetDateTime endTime;

  /**
   * The tenant this process instance belongs to.
   * <p>
   * Note: Might be null if no tenant is assigned.
   */
  protected String tenantId;

  /**
   * The current execution state of this process instance.
   */
  protected String state;
}
