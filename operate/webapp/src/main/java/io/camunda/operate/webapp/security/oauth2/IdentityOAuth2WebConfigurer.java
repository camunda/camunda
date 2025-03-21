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
package io.camunda.operate.webapp.security.oauth2;

import static com.nimbusds.jose.JOSEObjectType.JWT;
import static io.camunda.operate.OperateProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.BaseWebConfigurer.sendJSONErrorMessage;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.ES256;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.ES384;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.ES512;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS384;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS512;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import io.camunda.identity.sdk.IdentityConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
@Profile(IDENTITY_AUTH_PROFILE)
public class IdentityOAuth2WebConfigurer {

  public static final String SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_ISSUER_URI =
      "spring.security.oauth2.resourceserver.jwt.issuer-uri";
  // Where to find the public key to validate signature,
  // which was created from authorization server's private key
  public static final String SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI =
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri";

  public static final String JWKS_PATH = "/protocol/openid-connect/certs";

  private static final Logger LOGGER = LoggerFactory.getLogger(IdentityOAuth2WebConfigurer.class);

  private final Environment env;

  private final IdentityConfiguration identityConfiguration;

  private final IdentityJwt2AuthenticationTokenConverter jwtConverter;

  public IdentityOAuth2WebConfigurer(
      final Environment env,
      final IdentityConfiguration identityConfiguration,
      final IdentityJwt2AuthenticationTokenConverter jwtConverter) {
    this.env = env;
    this.identityConfiguration = identityConfiguration;
    this.jwtConverter = jwtConverter;
  }

  public void configure(final HttpSecurity http) throws Exception {
    if (isJWTEnabled()) {
      http.oauth2ResourceServer(
          serverCustomizer ->
              serverCustomizer
                  .authenticationEntryPoint(this::authenticationFailure)
                  .jwt(
                      jwtCustomizer ->
                          jwtCustomizer
                              .jwtAuthenticationConverter(jwtConverter)
                              .decoder(jwtDecoder())));
      LOGGER.info("Enabled OAuth2 JWT access to Operate API");
    }
  }

  /**
   * JwtDecoder that supports both the "jwt" (standard JWT) and "at+jwt" (Access Token JWT) JOSE
   * types for token validation.
   */
  private JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder.withJwkSetUri(getJwkSetUriProperty())
        .jwsAlgorithms(
            algorithms -> {
              algorithms.add(RS256);
              algorithms.add(RS384);
              algorithms.add(RS512);
              algorithms.add(ES256);
              algorithms.add(ES384);
              algorithms.add(ES512);
            })
        .jwtProcessorCustomizer(
            processor -> {
              processor.setJWSTypeVerifier(
                  new DefaultJOSEObjectTypeVerifier<>(JWT, new JOSEObjectType("at+jwt"), null));
            })
        .build();
  }

  private String getJwkSetUriProperty() {
    final String backendUri;

    // If the SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI is present, then it has already
    // been correctly
    // calculated and should be used as-is.
    if (env.containsProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI)) {
      backendUri = env.getProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI);
      LOGGER.info(
          "Using value in SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI for issuer authentication");
    } else {
      backendUri = identityConfiguration.getIssuerBackendUrl() + JWKS_PATH;
      LOGGER.warn(
          "SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI is not present, building issuer authentication uri from issuer backend url.");
    }

    LOGGER.info("Using {} for issuer authentication", backendUri);

    return backendUri;
  }

  private void authenticationFailure(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException e)
      throws IOException {
    sendJSONErrorMessage(response, e.getMessage());
  }

  protected boolean isJWTEnabled() {
    return env.containsProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_ISSUER_URI)
        || env.containsProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI);
  }
}
