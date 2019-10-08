/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.ui_configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class UIConfigurationDto {

  private HeaderCustomizationDto header;
  private boolean emailEnabled;
  private boolean sharingEnabled;
  private String optimizeVersion;
  private Map<String, WebappsEndpointDto> webappsEndpoints;
}
