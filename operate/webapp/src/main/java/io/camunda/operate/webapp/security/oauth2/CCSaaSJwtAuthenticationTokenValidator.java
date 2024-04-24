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

import static io.camunda.operate.OperateProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.operate.util.CollectionUtil.firstOrDefault;
import static io.camunda.operate.util.CollectionUtil.getOrDefaultFromMap;
import static io.camunda.operate.util.ConversionUtils.stringIsEmpty;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@Profile("!" + IDENTITY_AUTH_PROFILE)
public class CCSaaSJwtAuthenticationTokenValidator implements JwtAuthenticationTokenValidator {

  public static final String AUDIENCE = "aud";
  public static final String CLUSTER_ID_CLAIM = "https://camunda.com/clusterId";
  protected final Logger logger = LoggerFactory.getLogger(getClass());
  @Autowired private OperateProperties operateProperties;

  @Override
  public boolean isValid(final JwtAuthenticationToken token) {
    final Map<String, Object> payload = token.getTokenAttributes();
    return isValid(payload);
  }

  private boolean isValid(final Map<String, Object> payload) {
    try {
      return getClusterId(payload).equals(getClusterIdFromConfiguration())
          && getAudience(payload).equals(getAudienceFromConfiguration());
    } catch (final Exception e) {
      logger.error(
          String.format(
              "Validation of JWT payload failed due to %s. Request is not authenticated.",
              e.getMessage()),
          e);
      return false;
    }
  }

  private String getClusterId(final Map<String, Object> payload) {
    final Object clusterIdObject = payload.get(CLUSTER_ID_CLAIM);
    if (clusterIdObject == null) {
      throw new OperateRuntimeException(
          "Couldn't get clusterId from JWT payload. Maybe wrong clusterId configuration?");
    }
    if (clusterIdObject instanceof String) {
      return (String) clusterIdObject;
    }
    if (clusterIdObject instanceof List) {
      return firstOrDefault(
          (List<String>) getOrDefaultFromMap(payload, AUDIENCE, Collections.emptyList()), null);
    }
    throw new OperateRuntimeException(
        "Couldn't get clusterId from JWT payload as String or list of Strings. Maybe wrong clusterId configuration?");
  }

  private String getAudience(final Map<String, Object> payload) {
    final Object audienceObject = payload.get(AUDIENCE);
    if (audienceObject == null) {
      throw new OperateRuntimeException("Couldn't get audience from JWT payload.");
    }
    if (audienceObject instanceof String) {
      return (String) audienceObject;
    }
    if (audienceObject instanceof List) {
      return ((List<String>) audienceObject).get(0);
    }
    throw new OperateRuntimeException(
        "Couldn't get audience from JWT payload as String or array of Strings.");
  }

  private String getClusterIdFromConfiguration() {
    String clusterId = operateProperties.getCloud().getClusterId();
    if (stringIsEmpty(clusterId)) {
      // fallback to old configuration from client properties
      logger.warn(
          "ClusterId should come from 'CAMUNDA_OPERATE_CLOUD_CLUSTERID' try 'CAMUNDA_OPERATE_CLIENT_CLUSTERID'");
      clusterId = operateProperties.getClient().getClusterId();
    }
    if (stringIsEmpty(clusterId)) {
      throw new OperateRuntimeException(
          "No configuration found in 'CAMUNDA_OPERATE_CLOUD_CLUSTERID' or 'CAMUNDA_OPERATE_CLIENT_CLUSTERID'");
    }
    return clusterId;
  }

  private String getAudienceFromConfiguration() {
    final String audience = operateProperties.getClient().getAudience();
    if (stringIsEmpty(audience)) {
      throw new OperateRuntimeException(
          "No configuration found in 'CAMUNDA_OPERATE_CLIENT_AUDIENCE'");
    }
    return audience;
  }
}
