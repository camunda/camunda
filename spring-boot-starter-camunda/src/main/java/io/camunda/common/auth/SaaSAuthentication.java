package io.camunda.common.auth;

import io.camunda.common.json.JsonMapper;
import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SaaSAuthentication extends JwtAuthentication {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final JsonMapper jsonMapper;

  public SaaSAuthentication(JwtConfig jwtConfig, JsonMapper jsonMapper) {
    super(jwtConfig);
    this.jsonMapper = jsonMapper;
  }

  public static SaaSAuthenticationBuilder builder() {
    return new SaaSAuthenticationBuilder();
  }

  private TokenResponse retrieveToken(Product product, JwtCredential jwtCredential) {
    try (CloseableHttpClient client = HttpClients.createDefault()) {
      HttpPost request = buildRequest(jwtCredential);
      return client.execute(
          request,
          response -> {
            try {
              return jsonMapper.fromJson(
                  EntityUtils.toString(response.getEntity()), TokenResponse.class);
            } catch (Exception e) {
              var errorMessage =
                  """
              Token retrieval failed from: {}
              Response code: {}
              Audience: {}
              """;
              LOG.error(
                  errorMessage,
                  jwtCredential.getAuthUrl(),
                  response.getCode(),
                  jwtCredential.getAudience());
              throw e;
            }
          });
    } catch (Exception e) {
      LOG.error("Authenticating for " + product + " failed due to " + e);
      throw new RuntimeException("Unable to authenticate", e);
    }
  }

  private HttpPost buildRequest(JwtCredential jwtCredential) {
    HttpPost httpPost = new HttpPost(jwtCredential.getAuthUrl());
    httpPost.addHeader("Content-Type", "application/json");
    TokenRequest tokenRequest =
        new TokenRequest(
            jwtCredential.getAudience(),
            jwtCredential.getClientId(),
            jwtCredential.getClientSecret());
    httpPost.setEntity(new StringEntity(jsonMapper.toJson(tokenRequest)));
    return httpPost;
  }

  @Override
  protected JwtToken generateToken(Product product, JwtCredential credential) {
    TokenResponse tokenResponse = retrieveToken(product, credential);
    return new JwtToken(
        tokenResponse.getAccessToken(),
        LocalDateTime.now().plusSeconds(tokenResponse.getExpiresIn()));
  }
}
