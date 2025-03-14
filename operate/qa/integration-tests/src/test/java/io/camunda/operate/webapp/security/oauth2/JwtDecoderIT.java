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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.camunda.identity.sdk.IdentityConfiguration;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

@WireMockTest
public class JwtDecoderIT {

  private final MockEnvironment environment = new MockEnvironment();
  @Mock private IdentityJwt2AuthenticationTokenConverter jwtConverter;
  private final WireMockRuntimeInfo wireMockInfo;
  private final String authServerMockUrl;
  private JwtDecoder decoder;

  public JwtDecoderIT(final WireMockRuntimeInfo wireMockInfo) {
    this.wireMockInfo = wireMockInfo;
    authServerMockUrl = wireMockInfo.getHttpBaseUrl();
  }

  @BeforeEach
  public void setup() {
    final IdentityConfiguration identityConfiguration =
        new IdentityConfiguration(null, authServerMockUrl, null, null, null);
    final IdentityOAuth2WebConfigurer identityOAuth2WebConfigurer =
        new IdentityOAuth2WebConfigurer(environment, identityConfiguration, jwtConverter);

    decoder = ReflectionTestUtils.invokeMethod(identityOAuth2WebConfigurer, "jwtDecoder");
  }

  @Test
  public void testDecodeRs256JwtWithNoAlgFieldInJwkResponse() throws JOSEException {
    // given
    final RSAKey rsaJWK = getRsaJWK(JWSAlgorithm.RS256);
    final String serializedJwt = signAndSerialize(rsaJWK, JWSAlgorithm.RS256);
    final String publicKey = rsaJWK.toPublicJWK().toJSONString();

    // remove alg field from mocked server response and assert it was removed to cover this use case
    final String withoutAlg = publicKey.replaceFirst("\"alg\":\".*\",", "");
    final String authServerResponseBody = "{\"keys\":[" + withoutAlg + "]}";
    System.out.println("authServerResponse=" + authServerResponseBody);
    assertFalse(authServerResponseBody.contains("alg\":"));

    wireMockInfo
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlMatching(".*/protocol/openid-connect/certs"))
                .willReturn(WireMock.jsonResponse(authServerResponseBody, HttpStatus.OK.value())));

    // when - then
    assertDoesNotThrow(() -> decoder.decode(serializedJwt));
    wireMockInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
  }

  @Test
  public void testDecodeES256JwtWithNoAlgFieldInJwkResponse() throws JOSEException {
    // given
    final ECKey ecJWK = getEcJWK(JWSAlgorithm.ES256, Curve.P_256);
    final String ecSerializedJwt = signAndSerialize(ecJWK, JWSAlgorithm.ES256);
    final String publicKey = ecJWK.toPublicJWK().toJSONString();
    System.out.println("publicKey=" + publicKey);

    // remove alg field from mocked server response and assert it was removed to cover this use case
    final String withoutAlg = publicKey.replaceFirst(",\"alg\":\".*\"", "");
    final String authServerResponseBody = "{\"keys\":[" + withoutAlg + "]}";
    System.out.println("authServerResponse=" + authServerResponseBody);
    assertFalse(authServerResponseBody.contains("alg\":"));

    wireMockInfo
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlMatching(".*/protocol/openid-connect/certs"))
                .willReturn(WireMock.jsonResponse(authServerResponseBody, HttpStatus.OK.value())));

    // when - then
    assertDoesNotThrow(() -> decoder.decode(ecSerializedJwt));
    wireMockInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
  }

  @Test
  public void testDecodeRS384Jwt() throws JOSEException {
    // given
    final RSAKey rsaJWK = getRsaJWK(JWSAlgorithm.RS384);
    final String serializedJwt = signAndSerialize(rsaJWK, JWSAlgorithm.RS384);
    final String publicKey = rsaJWK.toPublicJWK().toJSONString();

    final String authServerResponseBody = "{\"keys\":[" + publicKey + "]}";
    System.out.println("authServerResponse=" + authServerResponseBody);
    assertTrue(authServerResponseBody.contains("alg\":"));

    wireMockInfo
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlMatching(".*/protocol/openid-connect/certs"))
                .willReturn(WireMock.jsonResponse(authServerResponseBody, HttpStatus.OK.value())));

    // when - then
    assertDoesNotThrow(() -> decoder.decode(serializedJwt));
    wireMockInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
  }

  @Test
  public void testDecodeRS512Jwt() throws JOSEException {
    // given
    final RSAKey rsaJWK = getRsaJWK(JWSAlgorithm.RS512);
    final String serializedJwt = signAndSerialize(rsaJWK, JWSAlgorithm.RS512);
    final String publicKey = rsaJWK.toPublicJWK().toJSONString();

    final String authServerResponseBody = "{\"keys\":[" + publicKey + "]}";
    System.out.println("authServerResponse=" + authServerResponseBody);

    wireMockInfo
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlMatching(".*/protocol/openid-connect/certs"))
                .willReturn(WireMock.jsonResponse(authServerResponseBody, HttpStatus.OK.value())));

    // when - then
    assertDoesNotThrow(() -> decoder.decode(serializedJwt));
    wireMockInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
  }

  @Test
  public void testDecodeES384Jwt() throws JOSEException {
    // given
    final ECKey ecJWK = getEcJWK(JWSAlgorithm.ES384, Curve.P_384);
    final String ecSerializedJwt = signAndSerialize(ecJWK, JWSAlgorithm.ES384);
    final String publicKey = ecJWK.toPublicJWK().toJSONString();
    System.out.println("publicKey=" + publicKey);

    final String authServerResponseBody = "{\"keys\":[" + publicKey + "]}";
    System.out.println("authServerResponse=" + authServerResponseBody);

    wireMockInfo
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlMatching(".*/protocol/openid-connect/certs"))
                .willReturn(WireMock.jsonResponse(authServerResponseBody, HttpStatus.OK.value())));

    // when - then
    assertDoesNotThrow(() -> decoder.decode(ecSerializedJwt));
    wireMockInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
  }

  @Test
  public void testDecodeES512Jwt() throws JOSEException {
    // given
    final ECKey ecJWK = getEcJWK(JWSAlgorithm.ES512, Curve.P_521);
    final String ecSerializedJwt = signAndSerialize(ecJWK, JWSAlgorithm.ES512);
    final String publicKey = ecJWK.toPublicJWK().toJSONString();
    System.out.println("publicKey=" + publicKey);

    final String authServerResponseBody = "{\"keys\":[" + publicKey + "]}";
    System.out.println("authServerResponse=" + authServerResponseBody);

    wireMockInfo
        .getWireMock()
        .register(
            WireMock.get(WireMock.urlMatching(".*/protocol/openid-connect/certs"))
                .willReturn(WireMock.jsonResponse(authServerResponseBody, HttpStatus.OK.value())));

    // when - then
    assertDoesNotThrow(() -> decoder.decode(ecSerializedJwt));
    wireMockInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
  }

  private RSAKey getRsaJWK(final JWSAlgorithm alg) throws JOSEException {
    return new RSAKeyGenerator(2048)
        .keyID("123")
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(alg)
        .generate();
  }

  private ECKey getEcJWK(final JWSAlgorithm alg, final Curve curve) throws JOSEException {
    return new ECKeyGenerator(curve)
        .algorithm(alg)
        .keyUse(KeyUse.SIGNATURE)
        .keyID("345")
        .generate();
  }

  private String signAndSerialize(final RSAKey rsaKey, final JWSAlgorithm alg)
      throws JOSEException {
    // Create RSA-signer with the private key
    final JWSSigner rsaSigner = new RSASSASigner(rsaKey);

    final SignedJWT rsaSignedJWT =
        new SignedJWT(
            new JWSHeader.Builder(alg).type(JOSEObjectType.JWT).keyID(rsaKey.getKeyID()).build(),
            getClaimsSet());

    rsaSignedJWT.sign(rsaSigner);
    final String serializedJwt = rsaSignedJWT.serialize();
    System.out.println("JWT serialized=" + serializedJwt);
    return serializedJwt;
  }

  private String signAndSerialize(final ECKey ecKey, final JWSAlgorithm alg) throws JOSEException {
    // Create EC-signer with the private key
    final ECDSASigner ecSigner = new ECDSASigner(ecKey);

    final SignedJWT ecSignedJWT =
        new SignedJWT(
            new JWSHeader.Builder(alg).type(JOSEObjectType.JWT).keyID(ecKey.getKeyID()).build(),
            getClaimsSet());

    ecSignedJWT.sign(ecSigner);
    final String ecSerializedJwt = ecSignedJWT.serialize();
    System.out.println("JWT serialized=" + ecSerializedJwt);
    return ecSerializedJwt;
  }

  private JWTClaimsSet getClaimsSet() {
    // prepare default JWT claims set
    return new JWTClaimsSet.Builder()
        .subject("alice")
        .issuer("http://localhost")
        .expirationTime(new Date(new Date().getTime() + 60 * 1000))
        .build();
  }
}
