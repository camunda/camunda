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
package io.camunda.tasklist.webapp.security.sso.model;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.sso.TokenAuthentication;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class C8ConsoleService {
  private static final Logger LOGGER = LoggerFactory.getLogger(C8ConsoleService.class);

  private static final String CONSOLE_CLUSTER_TEMPLATE = "%s/external/organizations/%s/clusters";
  @Autowired private TasklistProperties tasklistProperties;

  @Autowired
  @Qualifier("auth0_restTemplate")
  private RestTemplate restTemplate;

  private ClusterMetadata clusterMetadata;

  public ClusterMetadata getClusterMetadata() {
    if (clusterMetadata == null) {
      try {
        clusterMetadata = retrieveClusterMetadata();
      } catch (Exception e) {
        LOGGER.error("Couldn't retrieve ClusterMetadata, return null.", e);
        clusterMetadata = null;
      }
    }
    return clusterMetadata;
  }

  private ClusterMetadata retrieveClusterMetadata() {
    final TokenAuthentication authentication =
        (TokenAuthentication) SecurityContextHolder.getContext().getAuthentication();
    if (authentication.isAuthenticated()) {
      final String operationName = "retrieve cluster metadata";
      final RetryPolicy<ClusterMetadata> retryPolicy =
          new RetryPolicy<ClusterMetadata>()
              .handle(IOException.class)
              .withDelay(Duration.ofMillis(500))
              .withMaxAttempts(5)
              .onRetry(e -> LOGGER.debug("Retrying #{} {}", e.getAttemptCount(), operationName))
              .onAbort(e -> LOGGER.error("Abort {} by {}", operationName, e.getFailure()))
              .onRetriesExceeded(
                  e ->
                      LOGGER.error(
                          "Retries {} exceeded for {}", e.getAttemptCount(), operationName));
      final ClusterMetadata clusterMetadata =
          Failsafe.with(retryPolicy)
              .get(() -> getClusterMetadataFromConsole(authentication.getAccessToken()));
      if (clusterMetadata != null) {
        return addModelerAndConsoleLinksIfNotExists(clusterMetadata);
      }
    }
    return null;
  }

  private ClusterMetadata addModelerAndConsoleLinksIfNotExists(ClusterMetadata clusterMetadata) {
    final Map<ClusterMetadata.AppName, String> urls = new TreeMap<>(clusterMetadata.getUrls());
    final String organizationId = tasklistProperties.getAuth0().getOrganization();
    final String domain = tasklistProperties.getCloud().getPermissionAudience();
    final String clusterId = tasklistProperties.getCloud().getClusterId();
    if (!urls.containsKey(ClusterMetadata.AppName.MODELER)) {
      urls.put(
          ClusterMetadata.AppName.MODELER,
          String.format(
              "https://%s.%s/org/%s", ClusterMetadata.AppName.MODELER, domain, organizationId));
    }
    if (!urls.containsKey(ClusterMetadata.AppName.CONSOLE)) {
      urls.put(
          ClusterMetadata.AppName.CONSOLE,
          String.format(
              "https://%s.%s/org/%s/cluster/%s",
              ClusterMetadata.AppName.CONSOLE, domain, organizationId, clusterId));
    }
    clusterMetadata.setUrls(urls);
    return clusterMetadata;
  }

  private ClusterMetadata getClusterMetadataFromConsole(final String accessToken) {
    final HttpHeaders headers = new HttpHeaders();
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    headers.setBearerAuth(accessToken);
    final String url =
        String.format(
            CONSOLE_CLUSTER_TEMPLATE,
            tasklistProperties.getCloud().getConsoleUrl(),
            tasklistProperties.getAuth0().getOrganization());
    final ResponseEntity<ClusterMetadata[]> response =
        restTemplate.exchange(
            url, HttpMethod.GET, new HttpEntity<>(headers), ClusterMetadata[].class);

    final ClusterMetadata[] clustersMetadata = response.getBody();
    if (clustersMetadata != null) {
      final Optional<ClusterMetadata> clusterMetadataMaybe =
          Arrays.stream(clustersMetadata)
              .filter(cm -> cm.getUuid().equals(tasklistProperties.getCloud().getClusterId()))
              .findFirst();
      return clusterMetadataMaybe.orElse(null);
    } else {
      return null;
    }
  }
}
