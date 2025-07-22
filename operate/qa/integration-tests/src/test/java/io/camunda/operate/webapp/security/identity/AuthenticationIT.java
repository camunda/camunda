/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.identity;

import static io.camunda.operate.OperateProfileService.IDENTITY_AUTH_PROFILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authorizations.Authorizations;
import io.camunda.identity.sdk.authorizations.dto.Authorization;
import io.camunda.identity.sdk.impl.rest.exception.RestException;
import io.camunda.identity.sdk.tenants.Tenants;
import io.camunda.identity.sdk.tenants.dto.Tenant;
import io.camunda.operate.util.SpringContextHolder;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.security.tenant.OperateTenant;
import io.camunda.security.configuration.AuthorizationsConfiguration;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      IdentityAuthentication.class,
      CamundaSecurityProperties.class
    },
    properties = {
      "camunda.operate.identity.issuerUrl=http://localhost:18080/auth/realms/camunda-platform",
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({IDENTITY_AUTH_PROFILE, "test"})
public class AuthenticationIT {

  @MockBean private Identity identity;

  @MockBean private Authorizations authorizations;

  @MockBean private Tenants tenants;

  @Mock private Tokens tokens;

  @Autowired @InjectMocks private IdentityAuthentication identityAuthentication;

  @SpyBean private SecurityConfiguration securityConfiguration;

  @Autowired private ApplicationContext applicationContext;

  @Before
  public void setup() {
    new SpringContextHolder().setApplicationContext(applicationContext);
    doReturn(null).when(tokens).getAccessToken();
    ReflectionTestUtils.setField(identityAuthentication, "tokens", tokens);
    ReflectionTestUtils.setField(identityAuthentication, "authorizations", null);
    ReflectionTestUtils.setField(identityAuthentication, "tenants", null);
    doReturn(authorizations).when(identity).authorizations();
    doReturn(tenants).when(identity).tenants();
  }

  public void cleanup() {}

  @Test
  public void shouldReturnAuthorizationsWhenFeatureIsEnabled() throws IOException {
    // when resource permissions are enabled and Identity returns mocked permissions
    final var authorizationsConfiguration = new AuthorizationsConfiguration();
    authorizationsConfiguration.setEnabled(true);
    doReturn(authorizationsConfiguration).when(securityConfiguration).getAuthorizations();
    final List<Authorization> permissions =
        new ObjectMapper()
            .readValue(
                getClass().getResource("/security/identity/authorizations.json"),
                new TypeReference<>() {});
    doReturn(permissions).when(authorizations).forToken(any());

    // then permissions are properly converted and returned by identityAuthentication
    final List<IdentityAuthorization> resourceBasedPermissions =
        identityAuthentication.getAuthorizations();
    assertThat(resourceBasedPermissions).hasSize(4);
    assertThat(resourceBasedPermissions)
        .filteredOn(
            au ->
                au.getResourceType().equals("process-definition")
                    && au.getResourceKey().equals("*"))
        .hasSize(1)
        .extracting(IdentityAuthorization::getPermissions)
        .containsExactly(Set.of("READ"));
    assertThat(resourceBasedPermissions)
        .filteredOn(
            au ->
                au.getResourceType().equals("process-definition")
                    && au.getResourceKey().equals("orderProcess"))
        .hasSize(1)
        .extracting(IdentityAuthorization::getPermissions)
        .containsExactly(Set.of("UPDATE_PROCESS_INSTANCE", "DELETE_PROCESS_INSTANCE"));
    assertThat(resourceBasedPermissions)
        .filteredOn(
            au ->
                au.getResourceType().equals("decision-definition")
                    && au.getResourceKey().equals("*"))
        .hasSize(1)
        .extracting(IdentityAuthorization::getPermissions)
        .containsExactly(Set.of("READ"));
    assertThat(resourceBasedPermissions)
        .filteredOn(
            au ->
                au.getResourceType().equals("process-definition")
                    && au.getResourceKey().equals("invoice"))
        .hasSize(1)
        .extracting(IdentityAuthorization::getPermissions)
        .containsExactly(Set.of("UPDATE_PROCESS_INSTANCE", "DELETE_PROCESS_INSTANCE"));
  }

  @Test
  public void shouldReturnNullWhenFeatureIsDisabled() {
    // when resource permissions are disabled
    final var authorizationsConfiguration = new AuthorizationsConfiguration();
    authorizationsConfiguration.setEnabled(false);
    doReturn(authorizationsConfiguration).when(securityConfiguration).getAuthorizations();

    // then no Identity is called
    assertThat(identityAuthentication.getAuthorizations()).isNull();
    verifyNoInteractions(authorizations);
    verifyNoInteractions(identity);
  }

  @Test
  public void shouldReturnEmptyListNullWhenIdentityThrowsException() {
    // when resource permissions are enabled, but Identity call throws exception
    final var authorizationsConfiguration = new AuthorizationsConfiguration();
    authorizationsConfiguration.setEnabled(true);
    doReturn(authorizationsConfiguration).when(securityConfiguration).getAuthorizations();
    doThrow(new RestException("smth went wrong")).when(authorizations).forToken(any());

    assertThat(identityAuthentication.getAuthorizations()).hasSize(0);
  }

  @Test
  public void shouldReturnTenantsWhenMultiTenancyIsEnabled() throws IOException {
    final var multiTenancyConfiguration = new MultiTenancyConfiguration();
    multiTenancyConfiguration.setEnabled(true);
    doReturn(multiTenancyConfiguration).when(securityConfiguration).getMultiTenancy();

    final List<Tenant> tenants =
        new ObjectMapper()
            .readValue(
                getClass().getResource("/security/identity/tenants.json"),
                new TypeReference<>() {});
    doReturn(tenants).when(this.tenants).forToken(any());

    // then tenants are properly converted and returned by identityAuthentication
    final List<OperateTenant> returnedTenants = identityAuthentication.getTenants();
    assertThat(returnedTenants).hasSize(3);

    assertThat(returnedTenants)
        .filteredOn(t -> t.getTenantId().equals("<default>") && t.getName().equals("Default"))
        .hasSize(1);

    assertThat(returnedTenants)
        .filteredOn(t -> t.getTenantId().equals("tenant-a") && t.getName().equals("Tenant A"))
        .hasSize(1);

    assertThat(returnedTenants)
        .filteredOn(t -> t.getTenantId().equals("tenant-b") && t.getName().equals("Tenant B"))
        .hasSize(1);
  }

  @Test
  public void shouldReturnNullAsTenantsWhenMultiTenancyIsDisabled() {
    final var multiTenancyConfiguration = new MultiTenancyConfiguration();
    multiTenancyConfiguration.setEnabled(false);
    doReturn(multiTenancyConfiguration).when(securityConfiguration).getMultiTenancy();

    // then no Identity is called
    assertThat(identityAuthentication.getTenants()).isNull();
    verifyNoInteractions(authorizations);
    verifyNoInteractions(tenants);
  }

  @Test
  public void shouldReturnEmptyListOfTenantsWhenIdentityThrowsException() {
    final var multiTenancyConfiguration = new MultiTenancyConfiguration();
    multiTenancyConfiguration.setEnabled(true);
    doReturn(multiTenancyConfiguration).when(securityConfiguration).getMultiTenancy();
    doThrow(new RestException("smth went wrong")).when(tenants).forToken(any());

    assertThat(identityAuthentication.getTenants()).hasSize(0);
  }
}
