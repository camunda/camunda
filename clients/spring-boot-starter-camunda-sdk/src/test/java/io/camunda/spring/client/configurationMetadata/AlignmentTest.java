/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.spring.client.configurationMetadata;

import static io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder.DEFAULT_CREDENTIALS_CACHE_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.camunda.spring.client.CamundaClientPropertiesTestConfig;
import io.camunda.spring.client.properties.CamundaClientProperties;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.ResourceUtils;
import org.springframework.util.unit.DataSize;

@SpringBootTest(classes = CamundaClientPropertiesTestConfig.class)
public class AlignmentTest {
  private static final Map<String, Function<CamundaClientProperties, Object>> GETTERS =
      new HashMap<>();
  private static final Map<String, Function<Object, Object>> MAPPERS = new HashMap<>();

  static {
    // camunda.client.execution-threads
    GETTERS.put("camunda.client.execution-threads", CamundaClientProperties::getExecutionThreads);
    // camunda.client.message-time-to-live
    GETTERS.put(
        "camunda.client.message-time-to-live", CamundaClientProperties::getMessageTimeToLive);
    MAPPERS.put("camunda.client.message-time-to-live", p -> Duration.parse((String) p));
    // camunda.client.max-message-size
    GETTERS.put("camunda.client.max-message-size", CamundaClientProperties::getMaxMessageSize);
    MAPPERS.put("camunda.client.max-message-size", p -> DataSize.parse((String) p));
    // camunda.client.max-metadata-size
    GETTERS.put("camunda.client.max-metadata-size", CamundaClientProperties::getMaxMetadataSize);
    MAPPERS.put("camunda.client.max-metadata-size", p -> DataSize.parse((String) p));
    // camunda.client.prefer-rest-over-grpc
    GETTERS.put(
        "camunda.client.prefer-rest-over-grpc", CamundaClientProperties::getPreferRestOverGrpc);
    // camunda.client.rest-address
    GETTERS.put("camunda.client.rest-address", CamundaClientProperties::getRestAddress);
    MAPPERS.put("camunda.client.rest-address", p -> URI.create((String) p));
    // camunda.client.grpc-address
    GETTERS.put("camunda.client.grpc-address", CamundaClientProperties::getGrpcAddress);
    MAPPERS.put("camunda.client.grpc-address", p -> URI.create((String) p));
    // camunda.client.request-timeout
    GETTERS.put("camunda.client.request-timeout", CamundaClientProperties::getRequestTimeout);
    MAPPERS.put("camunda.client.request-timeout", p -> Duration.parse((String) p));
    // camunda.client.request-timeout-offset
    GETTERS.put(
        "camunda.client.request-timeout-offset", CamundaClientProperties::getRequestTimeoutOffset);
    MAPPERS.put("camunda.client.request-timeout-offset", p -> Duration.parse((String) p));
    // camunda.client.tenant-id
    GETTERS.put("camunda.client.tenant-id", CamundaClientProperties::getTenantId);
    // camunda.client.auth.credentials-cache-path
    GETTERS.put(
        "camunda.client.auth.credentials-cache-path", p -> p.getAuth().getCredentialsCachePath());
    MAPPERS.put("camunda.client.auth.credentials-cache-path", p -> DEFAULT_CREDENTIALS_CACHE_PATH);
    // camunda.client.auth.connect-timeout
    GETTERS.put("camunda.client.auth.connect-timeout", p -> p.getAuth().getConnectTimeout());
    MAPPERS.put("camunda.client.auth.connect-timeout", p -> Duration.parse((String) p));
    // camunda.client.auth.read-timeout
    GETTERS.put("camunda.client.auth.read-timeout", p -> p.getAuth().getReadTimeout());
    MAPPERS.put("camunda.client.auth.read-timeout", p -> Duration.parse((String) p));
    // camunda.client.worker.defaults.tenant-ids
    GETTERS.put(
        "camunda.client.worker.defaults.tenant-ids",
        p -> p.getWorker().getDefaults().getTenantIds());
    // camunda.client.worker.defaults.timeout
    GETTERS.put(
        "camunda.client.worker.defaults.timeout", p -> p.getWorker().getDefaults().getTimeout());
    MAPPERS.put("camunda.client.worker.defaults.timeout", p -> Duration.parse((String) p));
    // camunda.client.worker.defaults.max-jobs-active
    GETTERS.put(
        "camunda.client.worker.defaults.max-jobs-active",
        p -> p.getWorker().getDefaults().getMaxJobsActive());
    // camunda.client.worker.defaults.poll-interval
    GETTERS.put(
        "camunda.client.worker.defaults.poll-interval",
        p -> p.getWorker().getDefaults().getPollInterval());
    MAPPERS.put("camunda.client.worker.defaults.poll-interval", p -> Duration.parse((String) p));
    // camunda.client.worker.defaults.name
    GETTERS.put("camunda.client.worker.defaults.name", p -> p.getWorker().getDefaults().getName());
    // camunda.client.worker.defaults.stream-enabled
    GETTERS.put(
        "camunda.client.worker.defaults.stream-enabled",
        p -> p.getWorker().getDefaults().getStreamEnabled());
  }

  @Autowired CamundaClientProperties camundaClientProperties;

  @TestFactory
  Stream<DynamicTest> alignmentTest() throws IOException {
    final ObjectMapper objectMapper = new ObjectMapper();
    final JsonNode jsonNode =
        objectMapper.readTree(
            ResourceUtils.getFile(
                "classpath:META-INF/additional-spring-configuration-metadata.json"));
    final ArrayNode properties = (ArrayNode) jsonNode.get("properties");
    return properties
        .valueStream()
        .filter(p -> p.has("defaultValue"))
        .map(
            p -> {
              final String name = p.get("name").asText();
              final Object defaultValue =
                  objectMapper.convertValue(p.get("defaultValue"), Object.class);
              return DynamicTest.dynamicTest(
                  "Property " + name + " with default value " + defaultValue,
                  () -> {
                    assertThat(GETTERS).containsKey(name);
                    final Object value = GETTERS.get(name).apply(camundaClientProperties);
                    final Object transformedDefaultValue =
                        MAPPERS.getOrDefault(name, o -> o).apply(defaultValue);
                    assertThat(value).isEqualTo(transformedDefaultValue);
                  });
            });
  }
}
