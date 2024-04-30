/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.tasklist.webapp.graphql.entity;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.tenant.TasklistTenant;
import java.util.List;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class UserDTO {

  @GraphQLField @GraphQLNonNull private String userId;
  @GraphQLField private String displayName;
  private boolean apiUser;
  @GraphQLField private List<Permission> permissions;
  @GraphQLField private List<String> roles;
  @GraphQLField private String salesPlanType;
  @GraphQLField private List<C8AppLink> c8Links = List.of();
  private List<TasklistTenant> tenants = List.of();
  private List<String> groups = List.of();

  public String getUserId() {
    return userId;
  }

  public UserDTO setUserId(final String userId) {
    this.userId = userId;
    return this;
  }

  public String getDisplayName() {
    if (!StringUtils.hasText(displayName)) {
      return userId;
    }
    return displayName;
  }

  public UserDTO setDisplayName(final String displayName) {
    this.displayName = displayName;
    return this;
  }

  public boolean isApiUser() {
    return apiUser;
  }

  public UserDTO setApiUser(boolean apiUser) {
    this.apiUser = apiUser;
    return this;
  }

  public List<Permission> getPermissions() {
    return permissions;
  }

  public UserDTO setPermissions(final List<Permission> permissions) {
    this.permissions = permissions;
    return this;
  }

  public List<String> getRoles() {
    return roles;
  }

  public UserDTO setRoles(final List<String> roles) {
    this.roles = roles;
    return this;
  }

  public UserDTO setSalesPlanType(final String salesPlanType) {
    this.salesPlanType = salesPlanType;
    return this;
  }

  public String getSalesPlanType() {
    return salesPlanType;
  }

  public List<C8AppLink> getC8Links() {
    return c8Links;
  }

  public UserDTO setC8Links(final List<C8AppLink> c8Links) {
    if (c8Links != null) {
      this.c8Links = c8Links;
    }
    return this;
  }

  public List<TasklistTenant> getTenants() {
    return tenants;
  }

  public UserDTO setTenants(List<TasklistTenant> tenants) {
    if (tenants != null) {
      this.tenants = tenants;
    }
    return this;
  }

  public List<String> getGroups() {
    return groups;
  }

  public UserDTO setGroups(List<String> groups) {
    this.groups = groups;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final UserDTO userDTO = (UserDTO) o;
    return apiUser == userDTO.apiUser
        && Objects.equals(userId, userDTO.userId)
        && Objects.equals(displayName, userDTO.displayName)
        && Objects.equals(permissions, userDTO.permissions)
        && Objects.equals(roles, userDTO.roles)
        && Objects.equals(salesPlanType, userDTO.salesPlanType)
        && Objects.equals(c8Links, userDTO.c8Links)
        && Objects.equals(tenants, userDTO.tenants)
        && Objects.equals(groups, userDTO.groups);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        userId, displayName, apiUser, permissions, roles, salesPlanType, c8Links, tenants, groups);
  }

  @Override
  public String toString() {
    return "UserDTO{"
        + "userId='"
        + userId
        + '\''
        + ", displayName='"
        + displayName
        + '\''
        + ", apiUser="
        + apiUser
        + ", permissions="
        + permissions
        + ", roles="
        + roles
        + ", salesPlanType='"
        + salesPlanType
        + '\''
        + ", c8Links="
        + c8Links
        + ", tenants="
        + tenants
        + ", groups="
        + groups
        + '}';
  }
}
