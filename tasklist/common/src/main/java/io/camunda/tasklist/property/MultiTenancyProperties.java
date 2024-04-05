/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.property;

public class MultiTenancyProperties {

  private boolean enabled = false;

  public boolean isEnabled() {
    return enabled;
  }

  public MultiTenancyProperties setEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }
}
