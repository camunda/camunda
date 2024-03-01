/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.zeebeimport;

import com.auth0.jwt.JWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import java.util.Date;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Class to get and cache M2M Auth0 access token. */
@Component
@Configuration
public class M2mTokenManager {

  protected static final String FIELD_NAME_GRANT_TYPE = "grant_type";
  protected static final String GRANT_TYPE_VALUE = "client_credentials";
  protected static final String FIELD_NAME_CLIENT_ID = "client_id";
  protected static final String FIELD_NAME_CLIENT_SECRET = "client_secret";
  protected static final String FIELD_NAME_AUDIENCE = "audience";
  protected static final String FIELD_NAME_ACCESS_TOKEN = "access_token";

  @Autowired private OperateProperties operateProperties;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private RestTemplateBuilder builder;

  /** Cached token. */
  private String token;

  private Date tokenExpiresAt;
  private final Object cacheLock = new Object();

  @Bean(name = "incidentNotificationRestTemplate")
  public RestTemplate getRestTemplate() {
    return builder.build();
  }

  public String getToken() {
    return getToken(false);
  }

  public String getToken(final boolean forceTokenUpdate) {
    if (token == null || tokenIsExpired() || forceTokenUpdate) {
      synchronized (cacheLock) {
        if (token == null || tokenIsExpired() || forceTokenUpdate) {
          token = getNewToken();
          tokenExpiresAt = JWT.decode(token).getExpiresAt();
        }
      }
    }
    return token;
  }

  private boolean tokenIsExpired() {
    // if tokenExpiresAt == null, we consider the token to be valid and will rely on 401 response
    // code for incident notification
    return tokenExpiresAt != null && !tokenExpiresAt.after(new Date());
  }

  private String getNewToken() {
    final String tokenURL =
        String.format("https://%s/oauth/token", operateProperties.getAuth0().getDomain());
    final Object request = createGetTokenRequest();

    final ResponseEntity<Map> response =
        getRestTemplate().postForEntity(tokenURL, request, Map.class);
    if (!response.getStatusCode().equals(HttpStatus.OK)) {
      throw new OperateRuntimeException(
          String.format(
              "Unable to get the M2M auth token, response status: %s.", response.getStatusCode()));
    }
    return (String) response.getBody().get(FIELD_NAME_ACCESS_TOKEN);
  }

  private ObjectNode createGetTokenRequest() {
    final ObjectNode request =
        objectMapper
            .createObjectNode()
            .put(FIELD_NAME_GRANT_TYPE, GRANT_TYPE_VALUE)
            .put(FIELD_NAME_CLIENT_ID, operateProperties.getAuth0().getM2mClientId())
            .put(FIELD_NAME_CLIENT_SECRET, operateProperties.getAuth0().getM2mClientSecret())
            .put(FIELD_NAME_AUDIENCE, operateProperties.getAuth0().getM2mAudience());
    return request;
  }

  /** Only for test usage. */
  protected void clearCache() {
    token = null;
    tokenExpiresAt = null;
  }
}
