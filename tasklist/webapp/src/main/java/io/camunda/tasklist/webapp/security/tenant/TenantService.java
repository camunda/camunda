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
package io.camunda.tasklist.webapp.security.tenant;

import static java.util.Collections.emptyList;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class TenantService {

  private static final Logger LOGGER = LoggerFactory.getLogger(TenantService.class);

  @Autowired private TasklistProperties tasklistProperties;

  public AuthenticatedTenants getAuthenticatedTenants() {
    if (!isMultiTenancyEnabled()) {
      // disabled means no tenant check necessary.
      // thus, the user/app has access to all tenants.
      return AuthenticatedTenants.allTenants();
    }

    final var authentication = getCurrentTenantAwareAuthentication();
    final var tenants = getTenantsFromAuthentication(authentication);

    if (CollectionUtils.isNotEmpty(tenants)) {
      return AuthenticatedTenants.assignedTenants(tenants);
    } else {
      return AuthenticatedTenants.noTenantsAssigned();
    }
  }

  public Boolean isTenantValid(final String tenantId) {
    if (isMultiTenancyEnabled()) {
      return getAuthenticatedTenants().contains(tenantId);
    } else {
      return true;
    }
  }

  private TenantAwareAuthentication getCurrentTenantAwareAuthentication() {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    final TenantAwareAuthentication currentAuthentication;

    if (authentication instanceof TenantAwareAuthentication tenantAwareAuthentication) {
      currentAuthentication = tenantAwareAuthentication;
    } else {
      currentAuthentication = null;
      // log error message for visibility
      final var message =
          String.format(
              "Multi Tenancy is not supported with current authentication type %s",
              authentication.getClass());
      LOGGER.error(message, new TasklistRuntimeException());
    }

    return currentAuthentication;
  }

  private List<String> getTenantsFromAuthentication(
      final TenantAwareAuthentication authentication) {
    final var authenticatedTenants = new ArrayList<String>();

    if (authentication != null) {
      final var tenants = authentication.getTenants();
      if (tenants != null && !tenants.isEmpty()) {
        tenants.stream()
            .map(TasklistTenant::getId)
            .collect(Collectors.toCollection(() -> authenticatedTenants));
      }
    }

    return authenticatedTenants;
  }

  public boolean isMultiTenancyEnabled() {
    return tasklistProperties.getMultiTenancy().isEnabled()
        && SecurityContextHolder.getContext().getAuthentication() != null;
  }

  public static class AuthenticatedTenants {

    private final TenantAccessType tenantAccessType;
    private final List<String> ids;

    AuthenticatedTenants(final TenantAccessType tenantAccessType, final List<String> ids) {
      this.tenantAccessType = tenantAccessType;
      this.ids = ids;
    }

    public TenantAccessType getTenantAccessType() {
      return tenantAccessType;
    }

    public List<String> getTenantIds() {
      return ids;
    }

    public boolean contains(String tenantId) {
      return ids.contains(tenantId);
    }

    public static AuthenticatedTenants allTenants() {
      return new AuthenticatedTenants(TenantAccessType.TENANT_ACCESS_ALL, emptyList());
    }

    public static AuthenticatedTenants noTenantsAssigned() {
      return new AuthenticatedTenants(TenantAccessType.TENANT_ACCESS_NONE, emptyList());
    }

    public static AuthenticatedTenants assignedTenants(List<String> tenants) {
      return new AuthenticatedTenants(TenantAccessType.TENANT_ACCESS_ASSIGNED, tenants);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final AuthenticatedTenants that = (AuthenticatedTenants) o;
      return tenantAccessType == that.tenantAccessType && Objects.equals(ids, that.ids);
    }

    @Override
    public int hashCode() {
      return Objects.hash(tenantAccessType, ids);
    }
  }

  public static enum TenantAccessType {
    TENANT_ACCESS_ALL,
    TENANT_ACCESS_ASSIGNED,
    TENANT_ACCESS_NONE
  }
}
