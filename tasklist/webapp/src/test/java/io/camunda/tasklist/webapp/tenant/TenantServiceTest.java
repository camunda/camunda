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
package io.camunda.tasklist.webapp.tenant;

import static org.mockito.Mockito.*;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.tenant.TasklistTenant;
import io.camunda.tasklist.webapp.security.tenant.TenantAwareAuthentication;
import io.camunda.tasklist.webapp.security.tenant.TenantService;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
public class TenantServiceTest {

  @Spy private TasklistProperties tasklistProperties;
  @InjectMocks private TenantService instance;

  @Test
  void getAuthenticatedTenantsWhenMultiTenancyIsOff() {
    tasklistProperties.getMultiTenancy().setEnabled(false);
    Assertions.assertThat(instance.getAuthenticatedTenants())
        .isEqualTo(TenantService.AuthenticatedTenants.allTenants());
  }

  @Test
  void getAuthenticatedTenantsWhenMultiTenancyIsOn() {
    tasklistProperties.getMultiTenancy().setEnabled(true);
    prepareMocksForSecurityContext();

    final List<String> expectedListOfTenants = new ArrayList<String>();
    expectedListOfTenants.add("A");
    expectedListOfTenants.add("B");

    final TenantService.AuthenticatedTenants result = instance.getAuthenticatedTenants();
    Assertions.assertThat(result.getTenantIds()).isEqualTo(expectedListOfTenants);
    Assertions.assertThat(result.getTenantAccessType())
        .isEqualTo(TenantService.TenantAccessType.TENANT_ACCESS_ASSIGNED);
  }

  @Test
  void invalidTenant() {
    final String tenantId = "C";
    tasklistProperties.getMultiTenancy().setEnabled(true);
    prepareMocksForSecurityContext();
    Assertions.assertThat(instance.isTenantValid(tenantId)).isFalse();
  }

  @Test
  void validTenant() {
    final String tenantId = "A";
    tasklistProperties.getMultiTenancy().setEnabled(true);
    prepareMocksForSecurityContext();
    Assertions.assertThat(instance.isTenantValid(tenantId)).isTrue();
  }

  private void prepareMocksForSecurityContext() {
    final Authentication authentication =
        mock(Authentication.class, withSettings().extraInterfaces(TenantAwareAuthentication.class));
    final SecurityContext securityContext = mock(SecurityContext.class);
    when(securityContext.getAuthentication()).thenReturn(authentication);
    SecurityContextHolder.setContext(securityContext);

    final List<TasklistTenant> listOfTenants = new ArrayList<TasklistTenant>();
    listOfTenants.add(new TasklistTenant("A", "TenantA"));
    listOfTenants.add(new TasklistTenant("B", "TenantB"));
    when(((TenantAwareAuthentication) authentication).getTenants()).thenReturn(listOfTenants);
  }
}
