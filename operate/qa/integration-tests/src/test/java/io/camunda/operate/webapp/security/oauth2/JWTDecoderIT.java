package io.camunda.operate.webapp.security.oauth2;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.camunda.identity.sdk.IdentityConfiguration;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

// TODO: Shhould this be IT or Test?
public class JWTDecoderIT {

  @Test
  public void testDecodeJwt() throws JOSEException {
    // given
    MockEnvironment environment = new MockEnvironment();
    IdentityConfiguration identityConfiguration =
        new IdentityConfiguration(
            null, "http://localhost:18080/auth/realms/camunda-platform", null, null, null);

    JwtDecoder decoder = IdentityOAuth2WebConfigurer.jwtDecoder(environment, identityConfiguration);

    // // JWK jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
    // // JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(jwk));
    // JwtEncoder encoder = new NimbusJwtEncoder(new
    // ImmutableSecret<>("bar".getBytes()));
    //
    // JwsHeader jwsHeader = JwsHeader.with(SignatureAlgorithm.RS256).build();
    // JwtClaimsSet claimsSet = JwtClaimsSet.builder().claim("foo", "bar").build();
    // JwtEncoderParameters parameters = JwtEncoderParameters.from(jwsHeader,
    // claimsSet);
    //
    // Jwt encodedJwt = encoder.encode(parameters);
    //
    // Jwt token = buildJWT();

    // RSA signatures require a public and private RSA key pair, the public key
    // must be made known to the JWS recipient in order to verify the signatures
    RSAKey rsaJWK = new RSAKeyGenerator(2048).keyID("123").generate();
    RSAKey rsaPublicJWK = rsaJWK.toPublicJWK();

    // Create RSA-signer with the private key
    JWSSigner signer = new RSASSASigner(rsaJWK);

    // Prepare JWT with claims set
    JWTClaimsSet claimsSet =
        new JWTClaimsSet.Builder()
            .subject("alice")
            .issuer("https://c2id.com")
            .expirationTime(new Date(new Date().getTime() + 60 * 1000))
            .build();

    SignedJWT signedJWT =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(rsaJWK.getKeyID())
                .build(),
            claimsSet);

    signedJWT.sign(signer);
    String serializedJwt = signedJWT.serialize();
    System.out.println(serializedJwt);

    // JwtEncoder encoder =

    // when
    decoder.decode(serializedJwt);
  }

  private Jwt buildJWT() {
    return Jwt.withTokenValue("token")
        .audience(List.of("audience"))
        .header("alg", "HS256")
        .claim("scope", "scope")
        .build();
  }
}
