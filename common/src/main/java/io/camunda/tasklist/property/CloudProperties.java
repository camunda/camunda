/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.property;

import java.util.Objects;

public class CloudProperties {

  private String permissionUrl;
  private String permissionAudience;

  public String getPermissionUrl() {
    return permissionUrl;
  }

  public void setPermissionUrl(final String permissionUrl) {
    this.permissionUrl = permissionUrl;
  }

  public String getPermissionAudience() {
    return permissionAudience;
  }

  public void setPermissionAudience(final String permissionAudience) {
    this.permissionAudience = permissionAudience;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CloudProperties)) {
      return false;
    }
    final CloudProperties that = (CloudProperties) o;
    return Objects.equals(permissionUrl, that.permissionUrl)
        && Objects.equals(permissionAudience, that.permissionAudience);
  }

  @Override
  public int hashCode() {
    return Objects.hash(permissionUrl, permissionAudience);
  }
}
