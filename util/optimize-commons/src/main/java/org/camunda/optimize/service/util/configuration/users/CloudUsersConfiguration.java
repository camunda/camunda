/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.users;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudUsersConfiguration {

  private String accountsUrl;
  private String notificationsUrl;

  // Only here for backwards compatibility as the param got renamed to accountsUrl
  @Deprecated
  public void setUsersUrl(final String usersUrl) {
    this.accountsUrl = usersUrl;
  }

}
