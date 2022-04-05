/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import com.auth0.jwt.JWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Date;
import java.util.Map;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Class to get and cache M2M Auth0 access token.
 */
@Component
@Configuration
public class M2mTokenManager {

  protected static final String FIELD_NAME_GRANT_TYPE = "grant_type";
  protected static final String GRANT_TYPE_VALUE = "client_credentials";
  protected static final String FIELD_NAME_CLIENT_ID = "client_id";
  protected static final String FIELD_NAME_CLIENT_SECRET = "client_secret";
  protected static final String FIELD_NAME_AUDIENCE = "audience";
  protected static final String FIELD_NAME_ACCESS_TOKEN = "access_token";

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private RestTemplateBuilder builder;

  /**
   * Cached token.
   */
  private String token;
  private Date tokenExpiresAt;
  private Object cacheLock = new Object();

  @Bean(name="incidentNotificationRestTemplate")
  public RestTemplate getRestTemplate() {
    return builder.build();
  }

  public String getToken() {
    return getToken(false);
  }

  public String getToken(boolean forceTokenUpdate) {
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
    //if tokenExpiresAt == null, we consider the token to be valid and will rely on 401 response
    //code for incident notification
    return tokenExpiresAt != null && !tokenExpiresAt.after(new Date());
  }

  private String getNewToken() {
    String tokenURL = String
        .format("https://%s/oauth/token", operateProperties.getAuth0().getDomain());
    Object request = createGetTokenRequest();

    final ResponseEntity<Map> response = getRestTemplate().postForEntity(tokenURL, request, Map.class);
    if (!response.getStatusCode().equals(HttpStatus.OK)) {
      throw new OperateRuntimeException(String
          .format("Unable to get the M2M auth token, response status: %s.",
              response.getStatusCode()));
    }
    return (String)response.getBody().get(FIELD_NAME_ACCESS_TOKEN);
  }

  private ObjectNode createGetTokenRequest() {
    final ObjectNode request = objectMapper.createObjectNode()
        .put(FIELD_NAME_GRANT_TYPE, GRANT_TYPE_VALUE)
        .put(FIELD_NAME_CLIENT_ID, operateProperties.getAuth0().getM2mClientId())
        .put(FIELD_NAME_CLIENT_SECRET, operateProperties.getAuth0().getM2mClientSecret())
        .put(FIELD_NAME_AUDIENCE, operateProperties.getAuth0().getM2mAudience())
    ;
    return request;
  }

  /**
   * Only for test usage.
   */
  protected void clearCache() {
    token = null;
    tokenExpiresAt = null;
  }

}
