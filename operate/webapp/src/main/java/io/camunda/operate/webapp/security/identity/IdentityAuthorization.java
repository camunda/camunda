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
package io.camunda.operate.webapp.security.identity;

import io.camunda.identity.sdk.authorizations.dto.Authorization;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class IdentityAuthorization implements Serializable {

  private static final long serialVersionUID = 1L;

  private String resourceKey;
  private String resourceType;
  private Set<String> permissions;

  public static IdentityAuthorization createFrom(Authorization authorization) {
    return new IdentityAuthorization()
        .setResourceKey(authorization.getResourceKey())
        .setResourceType(authorization.getResourceType())
        .setPermissions(authorization.getPermissions());
  }

  public static List<IdentityAuthorization> createFrom(List<Authorization> authorizations) {
    if (authorizations == null) {
      return new ArrayList<>();
    }
    return authorizations.stream()
        .filter(Objects::nonNull)
        .map(IdentityAuthorization::createFrom)
        .collect(Collectors.toList());
  }

  public String getResourceKey() {
    return resourceKey;
  }

  public IdentityAuthorization setResourceKey(String resourceKey) {
    this.resourceKey = resourceKey;
    return this;
  }

  public String getResourceType() {
    return resourceType;
  }

  public IdentityAuthorization setResourceType(String resourceType) {
    this.resourceType = resourceType;
    return this;
  }

  public Set<String> getPermissions() {
    return permissions;
  }

  public IdentityAuthorization setPermissions(Set<String> permissions) {
    // Copy the container so that it remains independent of any changes to the original
    this.permissions = new HashSet<>(permissions);
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(resourceKey, resourceType, permissions);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IdentityAuthorization that = (IdentityAuthorization) o;
    return Objects.equals(resourceKey, that.resourceKey)
        && Objects.equals(resourceType, that.resourceType)
        && Objects.equals(permissions, that.permissions);
  }

  @Override
  public String toString() {
    return "IdentityAuthorization{"
        + "resourceKey='"
        + resourceKey
        + '\''
        + ", resourceType='"
        + resourceType
        + '\''
        + ", permissions="
        + permissions
        + '}';
  }
}
