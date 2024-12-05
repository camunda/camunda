package io.camunda.operate.webapp.security.oauth2;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.camunda.identity.sdk.IdentityConfiguration;
import java.util.Date;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

// TODO: Shhould this be IT or Test?
public class JWTDecoderIT {

  private static final int mockAuthServerPort = 19602;
  @Rule public WireMockRule wireMockRule = new WireMockRule(mockAuthServerPort);

  @Test
  public void testDecodeJwt() throws JOSEException {

    // given
    final String authServerMockUrl = urlFor("/mockAuthServer");

    final MockEnvironment environment = new MockEnvironment();

    final IdentityConfiguration identityConfiguration =
        new IdentityConfiguration(null, authServerMockUrl, null, null, null);

    final JwtDecoder decoder =
        IdentityOAuth2WebConfigurer.jwtDecoder(environment, identityConfiguration);

    // RSA signatures require a public and private RSA key pair, the public key
    // must be made known to the JWS recipient in order to verify the signatures
    final RSAKey rsaJWK = new RSAKeyGenerator(2048).keyID("123").generate();
    final RSAKey rsaPublicJWK = rsaJWK.toPublicJWK();

    // Create RSA-signer with the private key
    final JWSSigner signer = new RSASSASigner(rsaJWK);

    // Prepare JWT with claims set
    final JWTClaimsSet claimsSet =
        new JWTClaimsSet.Builder()
            .subject("alice")
            .issuer("https://c2id.com")
            .expirationTime(new Date(new Date().getTime() + 60 * 1000))
            .build();

    final SignedJWT signedJWT =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(rsaJWK.getKeyID())
                .build(),
            claimsSet);

    signedJWT.sign(signer);
    signedJWT.getHeader().getAlgorithm();
    final String serializedJwt = signedJWT.serialize();
    System.out.println(serializedJwt);

    final String authServerResponseBody = "{\"keys\":[" + rsaPublicJWK.toJSONString() + "]}";
    stubFor(
        get(urlMatching(".*/mockAuthServer/protocol/openid-connect/certs"))
            .willReturn(
                jsonResponse(authServerResponseBody, HttpStatus.OK.value())
                    .withHeader("TODO", "test"))); // TODO ?

    final String header = "{\"kid\": \"123\", \"typ\": \"JWT\"}"; // TODO generalize maybe
    final String withoutAlg =
        serializedJwt.replaceFirst("[^.]*", Base64.encode(header).toString()).replaceAll("=", "");
    System.out.println(withoutAlg);
    // when
    decoder.decode(serializedJwt); // TODO use withoutAlg once it works
  }

  private Jwt buildJWT() {
    return Jwt.withTokenValue("token")
        .audience(List.of("audience"))
        .header("alg", "HS256")
        .claim("scope", "scope")
        .build();
  }

  private String urlFor(final String path) {
    return String.format("http://localhost:%d%s", mockAuthServerPort, path);
  }
}
