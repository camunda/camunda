package io.camunda.operate.webapp.security.oauth2;

import io.camunda.identity.sdk.IdentityConfiguration;
import java.util.List;
import org.junit.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

// TODO: Shhould this be IT or Test?
public class JWTDecoderIT {

  @Test
  public void testDecodeJwt() {
    // given
    Environment environment = new StandardEnvironment();
    IdentityConfiguration identityConfiguration =
        new IdentityConfiguration(null, null, null, null, null);

    JwtDecoder decoder = IdentityOAuth2WebConfigurer.jwtDecoder(environment, identityConfiguration);

    Jwt token = buildJWT();

    //    JwtEncoder encoder =

    // when
    decoder.decode("foo");
  }

  private Jwt buildJWT() {
    return Jwt.withTokenValue("token")
        .audience(List.of("audience"))
        .header("alg", "HS256")
        .claim("scope", "scope")
        .build();
  }
}
