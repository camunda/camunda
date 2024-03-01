/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.rest.dto;

import io.camunda.operate.webapp.security.Permission;
import io.camunda.operate.webapp.security.sso.model.ClusterMetadata;
import io.camunda.operate.webapp.security.tenant.OperateTenant;
import java.util.*;
import org.springframework.util.StringUtils;

public class UserDto {

  private String userId;

  private String displayName;

  private boolean canLogout;

  private List<Permission> permissions;

  private List<OperateTenant> tenants;

  private List<String> roles;

  private String salesPlanType;

  private Map<ClusterMetadata.AppName, String> c8Links = new HashMap<>();

  public boolean isCanLogout() {
    return canLogout;
  }

  public UserDto setCanLogout(boolean canLogout) {
    this.canLogout = canLogout;
    return this;
  }

  public String getUserId() {
    return userId;
  }

  public UserDto setUserId(final String userId) {
    this.userId = userId;
    return this;
  }

  public String getDisplayName() {
    if (!StringUtils.hasText(displayName)) {
      return userId;
    }
    return displayName;
  }

  public UserDto setDisplayName(final String displayName) {
    this.displayName = displayName;
    return this;
  }

  // TODO: Remove when frontend has removed usage of username
  public String getUsername() {
    return getDisplayName();
  }

  public List<Permission> getPermissions() {
    return permissions;
  }

  public UserDto setPermissions(final List<Permission> permissions) {
    this.permissions = permissions;
    return this;
  }

  public List<OperateTenant> getTenants() {
    return tenants;
  }

  public UserDto setTenants(List<OperateTenant> tenants) {
    this.tenants = tenants;
    return this;
  }

  public List<String> getRoles() {
    return roles;
  }

  public UserDto setRoles(final List<String> roles) {
    this.roles = roles;
    return this;
  }

  public String getSalesPlanType() {
    return salesPlanType;
  }

  public UserDto setSalesPlanType(final String salesPlanType) {
    this.salesPlanType = salesPlanType;
    return this;
  }

  public Map<ClusterMetadata.AppName, String> getC8Links() {
    return c8Links;
  }

  public UserDto setC8Links(Map<ClusterMetadata.AppName, String> c8Links) {
    if (c8Links != null) {
      this.c8Links = c8Links;
    }
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, displayName, canLogout, permissions, roles, salesPlanType, c8Links);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final UserDto userDto = (UserDto) o;
    return canLogout == userDto.canLogout
        && userId.equals(userDto.userId)
        && displayName.equals(userDto.displayName)
        && permissions.equals(userDto.permissions)
        && Objects.equals(roles, userDto.roles)
        && Objects.equals(salesPlanType, userDto.salesPlanType)
        && Objects.equals(c8Links, userDto.c8Links);
  }
}
