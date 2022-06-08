/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.oauth2;

import static io.camunda.operate.webapp.security.OperateProfileService.IDENTITY_AUTH_PROFILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.auth0.jwt.exceptions.InvalidClaimException;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.Authentication;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
        TestApplicationWithNoBeans.class,
        IdentityJwt2AuthenticationTokenConverter.class
    }
)
@ActiveProfiles({IDENTITY_AUTH_PROFILE, "test"})
public class IdentityJwt2AuthenticationTokenConverterTest {

  @Autowired
  @SpyBean
  private IdentityJwt2AuthenticationTokenConverter tokenConverter;

  @MockBean
  private Identity identity;
  @Mock
  private Authentication authentication;

  @Test(expected = InvalidClaimException.class)
  public void shouldFailIfIdentityVerificationFails(){
    when(identity.authentication()).thenThrow(
        new InvalidClaimException("The Claim 'aud' value doesn't contain the required audience."));
    final Jwt token = createJwtTokenWith();
    tokenConverter.convert(token);
  }

  @Test
  public void shouldConvert(){
    when(identity.authentication()).thenReturn(authentication);
    when(authentication.verifyToken(any())).thenReturn(null);
    final Jwt token = createJwtTokenWith();
    final AbstractAuthenticationToken authenticationToken = tokenConverter.convert(token);
    assertThat(authenticationToken).isInstanceOf(JwtAuthenticationToken.class);
    assertThat(authenticationToken.isAuthenticated()).isTrue();
  }

  protected Jwt createJwtTokenWith() {
    return Jwt.withTokenValue("token")
            .audience(List.of("audience"))
            .header("alg", "HS256")
            .claim("foo", "bar")
        .build();
  }
}
