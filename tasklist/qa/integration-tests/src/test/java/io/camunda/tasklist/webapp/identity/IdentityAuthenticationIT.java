/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.identity;

import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.impl.rest.exception.RestException;
import io.camunda.identity.sdk.tenants.Tenants;
import io.camunda.identity.sdk.tenants.dto.Tenant;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthentication;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthorization;
import io.camunda.tasklist.webapp.tenant.TasklistTenant;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      IdentityAuthentication.class,
      TasklistProperties.class
    },
    properties = {
      "camunda.tasklist.identity.issuerUrl=http://localhost:18080/auth/realms/camunda-platform",
      "management.endpoint.health.group.readiness.include=readinessState"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({IDENTITY_AUTH_PROFILE, "test"})
public class IdentityAuthenticationIT {

  @MockBean private Identity identity;

  @MockBean private IdentityAuthorization authorizations;

  @MockBean private Tenants tenants;

  @Mock private Tokens tokens;

  @Autowired @InjectMocks private IdentityAuthentication identityAuthentication;

  @SpyBean private SecurityConfiguration securityConfiguration;

  @Autowired private ApplicationContext applicationContext;

  @BeforeEach
  public void setup() {
    new SpringContextHolder().setApplicationContext(applicationContext);
    doReturn(null).when(tokens).getAccessToken();
    ReflectionTestUtils.setField(identityAuthentication, "tokens", tokens);
    ReflectionTestUtils.setField(identityAuthentication, "tenants", null);
    doReturn(tenants).when(identity).tenants();
  }

  @Test
  public void shouldReturnTenantsWhenMultiTenancyIsEnabled() throws IOException {
    // given
    final var multiTenancyConfiguration = new MultiTenancyConfiguration();
    multiTenancyConfiguration.setEnabled(true);
    when(securityConfiguration.getMultiTenancy()).thenReturn(multiTenancyConfiguration);

    final List<Tenant> tenants =
        new ObjectMapper()
            .readValue(getClass().getResource("/identity/tenants.json"), new TypeReference<>() {});
    when(this.tenants.forToken(any())).thenReturn(tenants);

    // when
    final List<TasklistTenant> result = identityAuthentication.getTenants();

    // then
    assertThat(result)
        .extracting("id", "name")
        .containsExactly(
            tuple("<default>", "Default"),
            tuple("tenant-a", "Tenant A"),
            tuple("tenant-b", "Tenant B"),
            tuple("tenant-c", "Tenant C"));
  }

  @Test
  public void shouldReturnEmptyListOfTenantsWhenMultiTenancyIsDisabled() {
    // given
    final var multiTenancyConfiguration = new MultiTenancyConfiguration();
    multiTenancyConfiguration.setEnabled(false);
    when(securityConfiguration.getMultiTenancy()).thenReturn(multiTenancyConfiguration);

    // when
    final var result = identityAuthentication.getTenants();

    // then
    assertThat(result).isEmpty();
    verifyNoInteractions(authorizations);
    verifyNoInteractions(tenants);
  }

  @Test
  public void shouldReturnEmptyListOfTenantsWhenIdentityThrowsException() {
    // given
    final var multiTenancyConfiguration = new MultiTenancyConfiguration();
    multiTenancyConfiguration.setEnabled(true);
    when(securityConfiguration.getMultiTenancy()).thenReturn(multiTenancyConfiguration);
    when(tenants.forToken(any())).thenThrow(new RestException("smth went wrong"));

    // when
    final var result = identityAuthentication.getTenants();

    // then
    assertThat(result).isEmpty();
  }
}
