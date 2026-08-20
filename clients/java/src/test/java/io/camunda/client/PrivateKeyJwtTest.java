/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import java.io.IOException;
import java.util.Objects;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class PrivateKeyJwtTest {

  @Container
  static KeycloakContainer keycloak =
      new KeycloakContainer().withRealmImportFile("privatekeyjwt/camunda-identity-test-realm.json");

  private static final String KEYSTORE_PATH =
      Objects.requireNonNull(
              PrivateKeyJwtTest.class.getClassLoader().getResource("privatekeyjwt/keystore.p12"))
          .getPath();

  @Test
  public void shouldReceiveAccessTokenFromIdP() throws IOException {
    final String tokenEndpoint =
        keycloak.getAuthServerUrl() + "/realms/camunda-identity-test/protocol/openid-connect/token";
    // when building private key JWT credentials
    final OAuthCredentialsProvider credentialsProvider =
        new OAuthCredentialsProviderBuilder()
            .authorizationServerUrl(tokenEndpoint)
            .clientId("camunda-test")
            .audience(tokenEndpoint)
            .clientAssertionKeystorePath(KEYSTORE_PATH)
            .clientAssertionKeystorePassword("password-store")
            .clientAssertionKeystoreKeyAlias("camunda-test-1-key")
            // WARNING some keystore generation tools will reuse the same password for both keystore
            // and private key. For more information see:
            // https://github.com/camunda/camunda/issues/36971#issuecomment-3224906376
            .clientAssertionKeystoreKeyPassword("password-store")
            .build();

    // the configured IdP should recognize them as valid and return an access token
    credentialsProvider.applyCredentials(
        (header, value) -> {
          assertThat(header).isEqualTo("Authorization");
          final String token = value.replaceFirst("Bearer ", "");
          final DecodedJWT jwt = JWT.decode(token);
          assertThat(jwt.getType()).isEqualTo("JWT");
        });
  }

  @Disabled("Example for MS Entra that requires a live App registration")
  @Test
  public void exampleEntraPrivateKeyJwtTest() throws IOException {
    // Create an App in App registration,
    // upload a certificate linked to KEYSTORE_PATH (example client.crt.pem provided)
    // and create at least one scope in Expose an API

    // Tenant ID aka Directory ID:
    final String entraTenantId = "<my-tenant-id>";
    // Application (client) ID from App registrations
    final String appClientId = "<my-client-id>";

    final String tokenEndpoint =
        "https://login.microsoftonline.com/" + entraTenantId + "/oauth2/v2.0/token";

    final OAuthCredentialsProvider credentialsProvider =
        new OAuthCredentialsProviderBuilder()
            .authorizationServerUrl(tokenEndpoint)
            .clientId(appClientId)
            .audience(tokenEndpoint)
            .clientAssertionKeystorePath(KEYSTORE_PATH)
            .clientAssertionKeystorePassword("password-store")
            .clientAssertionKeystoreKeyAlias("camunda-test-1-key")
            .clientAssertionKeystoreKeyPassword("password-store")
            // at least one scope needs to be added to "Expose an API",
            // after which the .default scope will work
            .scope("api://" + appClientId + "/.default")
            .build();

    credentialsProvider.applyCredentials(
        (header, value) -> {
          assertThat(header).isEqualTo("Authorization");
          final String token = value.replaceFirst("Bearer ", "");
          final DecodedJWT jwt = JWT.decode(token);
          assertThat(jwt.getType()).isEqualTo("JWT");
        });
  }
}
