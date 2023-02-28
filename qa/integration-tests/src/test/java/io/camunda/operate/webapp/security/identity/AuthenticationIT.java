/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.identity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authorizations.Authorizations;
import io.camunda.identity.sdk.authorizations.dto.Authorization;
import io.camunda.identity.sdk.impl.rest.exception.RestException;
import io.camunda.operate.property.IdentityProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.SpringContextHolder;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
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

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static io.camunda.operate.webapp.security.OperateProfileService.IDENTITY_AUTH_PROFILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
        TestApplicationWithNoBeans.class,
        IdentityAuthentication.class,
        OperateProperties.class
    },
    properties = {
        "camunda.operate.identity.issuerUrl=http://localhost:18080/auth/realms/camunda-platform",
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({IDENTITY_AUTH_PROFILE, "test"})
public class AuthenticationIT {

  @MockBean
  private Identity identity;

  @MockBean
  private Authorizations authorizations;

  @Mock
  private Tokens tokens;

  @Autowired
  @InjectMocks
  private IdentityAuthentication identityAuthentication;

  @SpyBean
  private OperateProperties operateProperties;

  @Autowired
  private ApplicationContext applicationContext;

  @Before
  public void setup() {
    new SpringContextHolder().setApplicationContext(applicationContext);
    doReturn(null).when(tokens).getAccessToken();
    ReflectionTestUtils.setField(identityAuthentication, "tokens", tokens);
    ReflectionTestUtils.setField(identityAuthentication, "authorizations", null);
    doReturn(authorizations).when(identity).authorizations();
  }

  public void cleanup() {

  }

  @Test
  public void shouldReturnAuthorizationsWhenFeatureIsEnabled() throws IOException {
    //when resource permissions are enabled and Identity returns mocked permissions
    doReturn(new IdentityProperties().setResourcePermissionsEnabled(true)).when(operateProperties).getIdentity();
    List<Authorization> permissions = new ObjectMapper().readValue(
        this.getClass().getResource("/security/identity/authorizations.json"), new TypeReference<>() {
        });
    doReturn(permissions).when(authorizations).forToken(any());

    //then permissions are properly converted and returned by identityAuthentication
    List<IdentityAuthorization> resourceBasedPermissions = identityAuthentication.getAuthorizations();
    assertThat(resourceBasedPermissions).hasSize(4);
    assertThat(resourceBasedPermissions).filteredOn(
            au -> au.getResourceType().equals("process-definition") && au.getResourceKey().equals("*")).hasSize(1)
        .extracting(IdentityAuthorization::getPermissions).containsExactly(Set.of("READ"));
    assertThat(resourceBasedPermissions).filteredOn(
            au -> au.getResourceType().equals("process-definition") && au.getResourceKey().equals("orderProcess"))
        .hasSize(1).extracting(IdentityAuthorization::getPermissions)
        .containsExactly(Set.of("UPDATE_PROCESS_INSTANCE", "DELETE_PROCESS_INSTANCE"));
    assertThat(resourceBasedPermissions).filteredOn(
            au -> au.getResourceType().equals("decision-definition") && au.getResourceKey().equals("*")).hasSize(1)
        .extracting(IdentityAuthorization::getPermissions).containsExactly(Set.of("READ"));
    assertThat(resourceBasedPermissions).filteredOn(
            au -> au.getResourceType().equals("process-definition") && au.getResourceKey().equals("invoice")).hasSize(1)
        .extracting(IdentityAuthorization::getPermissions)
        .containsExactly(Set.of("UPDATE_PROCESS_INSTANCE", "DELETE_PROCESS_INSTANCE"));
  }

  @Test
  public void shouldReturnNullWhenFeatureIsDisabled() {
    //when resource permissions are disabled
    doReturn(new IdentityProperties().setResourcePermissionsEnabled(false)).when(operateProperties).getIdentity();

    //then no Identity is called
    assertThat(identityAuthentication.getAuthorizations()).isNull();
    verifyNoInteractions(authorizations);
    verifyNoInteractions(identity);
  }

  @Test
  public void shouldReturnEmptyListNullWhenIdentityThrowsException() {
    //when resource permissions are enabled, but Identity call throws exception
    doReturn(new IdentityProperties().setResourcePermissionsEnabled(true)).when(operateProperties).getIdentity();
    doThrow(new RestException("smth went wrong")).when(authorizations).forToken(any());

    assertThat(identityAuthentication.getAuthorizations()).hasSize(0);
  }


}
