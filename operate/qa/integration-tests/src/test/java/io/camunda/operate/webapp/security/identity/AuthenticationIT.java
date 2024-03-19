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

import static io.camunda.operate.OperateProfileService.IDENTITY_AUTH_PROFILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authorizations.Authorizations;
import io.camunda.identity.sdk.authorizations.dto.Authorization;
import io.camunda.identity.sdk.impl.rest.exception.RestException;
import io.camunda.identity.sdk.tenants.Tenants;
import io.camunda.identity.sdk.tenants.dto.Tenant;
import io.camunda.operate.property.IdentityProperties;
import io.camunda.operate.property.MultiTenancyProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.SpringContextHolder;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.security.tenant.OperateTenant;
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
      OperateProperties.class
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

  @SpyBean private OperateProperties operateProperties;

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
    doReturn(new IdentityProperties().setResourcePermissionsEnabled(true))
        .when(operateProperties)
        .getIdentity();
    final List<Authorization> permissions =
        new ObjectMapper()
            .readValue(
                this.getClass().getResource("/security/identity/authorizations.json"),
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
    doReturn(new IdentityProperties().setResourcePermissionsEnabled(false))
        .when(operateProperties)
        .getIdentity();

    // then no Identity is called
    assertThat(identityAuthentication.getAuthorizations()).isNull();
    verifyNoInteractions(authorizations);
    verifyNoInteractions(identity);
  }

  @Test
  public void shouldReturnEmptyListNullWhenIdentityThrowsException() {
    // when resource permissions are enabled, but Identity call throws exception
    doReturn(new IdentityProperties().setResourcePermissionsEnabled(true))
        .when(operateProperties)
        .getIdentity();
    doThrow(new RestException("smth went wrong")).when(authorizations).forToken(any());

    assertThat(identityAuthentication.getAuthorizations()).hasSize(0);
  }

  @Test
  public void shouldReturnTenantsWhenMultiTenancyIsEnabled() throws IOException {
    final var multiTenancyProperties = mock(MultiTenancyProperties.class);
    doReturn(multiTenancyProperties).when(operateProperties).getMultiTenancy();
    doReturn(true).when(multiTenancyProperties).isEnabled();

    final List<Tenant> tenants =
        new ObjectMapper()
            .readValue(
                this.getClass().getResource("/security/identity/tenants.json"),
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
    final var multiTenancyProperties = mock(MultiTenancyProperties.class);
    doReturn(multiTenancyProperties).when(operateProperties).getMultiTenancy();
    doReturn(false).when(multiTenancyProperties).isEnabled();

    // then no Identity is called
    assertThat(identityAuthentication.getTenants()).isNull();
    verifyNoInteractions(authorizations);
    verifyNoInteractions(tenants);
  }

  @Test
  public void shouldReturnEmptyListOfTenantsWhenIdentityThrowsException() {
    final var multiTenancyProperties = mock(MultiTenancyProperties.class);
    doReturn(multiTenancyProperties).when(operateProperties).getMultiTenancy();
    doReturn(true).when(multiTenancyProperties).isEnabled();
    doThrow(new RestException("smth went wrong")).when(tenants).forToken(any());

    assertThat(identityAuthentication.getTenants()).hasSize(0);
  }
}
