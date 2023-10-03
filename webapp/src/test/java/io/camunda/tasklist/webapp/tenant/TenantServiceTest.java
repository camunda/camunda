/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
