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
package io.camunda.tasklist.webapp.security.oauth;

import io.camunda.identity.sdk.Identity;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.security.tenant.TasklistTenant;
import io.camunda.tasklist.webapp.security.tenant.TenantAwareAuthentication;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class IdentityTenantAwareJwtAuthenticationToken extends JwtAuthenticationToken
    implements TenantAwareAuthentication {

  private static final long serialVersionUID = 1L;

  private List<TasklistTenant> tenants = Collections.emptyList();

  public IdentityTenantAwareJwtAuthenticationToken(
      final Jwt jwt, final Collection<? extends GrantedAuthority> authorities, final String name) {
    super(jwt, authorities, name);
  }

  @Override
  public List<TasklistTenant> getTenants() {
    if (CollectionUtils.isEmpty(tenants) && isMultiTenancyEnabled()) {
      tenants = retrieveTenants();
    }
    return tenants;
  }

  private List<TasklistTenant> retrieveTenants() {
    try {
      final var token = getToken().getTokenValue();
      final var identityTenants = getIdentity().tenants().forToken(token);
      if (CollectionUtils.isEmpty(identityTenants)) {
        return Collections.emptyList();
      } else {
        return identityTenants.stream()
            .map((t) -> new TasklistTenant(t.getTenantId(), t.getName()))
            .sorted(TENANT_NAMES_COMPARATOR)
            .toList();
      }
    } catch (Exception e) {
      throw new InsufficientAuthenticationException(e.getMessage(), e);
    }
  }

  private Identity getIdentity() {
    return SpringContextHolder.getBean(Identity.class);
  }

  private boolean isMultiTenancyEnabled() {
    return SpringContextHolder.getBean(TasklistProperties.class).getMultiTenancy().isEnabled();
  }
}
