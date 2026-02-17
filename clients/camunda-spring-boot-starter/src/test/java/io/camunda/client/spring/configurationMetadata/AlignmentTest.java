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
package io.camunda.client.spring.configurationMetadata;

import static io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder.DEFAULT_CREDENTIALS_CACHE_PATH;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.camunda.client.spring.CamundaClientPropertiesTestConfig;
import io.camunda.client.spring.properties.CamundaClientLegacyPropertiesMapping;
import io.camunda.client.spring.properties.CamundaClientLegacyPropertiesMappingsLoader;
import io.camunda.client.spring.properties.CamundaClientProperties;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
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
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Function<JsonNode, Object> DURATION_MAPPER = p -> Duration.parse(p.asText());
  private static final Function<JsonNode, Object> DATA_SIZE_MAPPER =
      p -> DataSize.parse(p.asText());
  private static final Function<JsonNode, Object> URI_MAPPER = p -> URI.create(p.asText());
  private static final Map<String, Getter> NEW_GETTERS =
      Map.ofEntries(
          entry(
              "camunda.client.execution-threads",
              new Getter(CamundaClientProperties::getExecutionThreads)),
          entry(
              "camunda.client.message-time-to-live",
              new Getter(CamundaClientProperties::getMessageTimeToLive, DURATION_MAPPER)),
          entry(
              "camunda.client.max-message-size",
              new Getter(CamundaClientProperties::getMaxMessageSize, DATA_SIZE_MAPPER)),
          entry(
              "camunda.client.max-metadata-size",
              new Getter(CamundaClientProperties::getMaxMetadataSize, DATA_SIZE_MAPPER)),
          entry(
              "camunda.client.prefer-rest-over-grpc",
              new Getter(CamundaClientProperties::getPreferRestOverGrpc)),
          entry(
              "camunda.client.rest-address",
              new Getter(CamundaClientProperties::getRestAddress, URI_MAPPER)),
          entry(
              "camunda.client.grpc-address",
              new Getter(CamundaClientProperties::getGrpcAddress, URI_MAPPER)),
          entry(
              "camunda.client.request-timeout",
              new Getter(CamundaClientProperties::getRequestTimeout, DURATION_MAPPER)),
          entry(
              "camunda.client.request-timeout-offset",
              new Getter(CamundaClientProperties::getRequestTimeoutOffset, DURATION_MAPPER)),
          entry("camunda.client.tenant-id", new Getter(CamundaClientProperties::getTenantId)),
          entry(
              "camunda.client.auth.credentials-cache-path",
              new Getter(
                  p -> p.getAuth().getCredentialsCachePath(), p -> DEFAULT_CREDENTIALS_CACHE_PATH)),
          entry(
              "camunda.client.auth.connect-timeout",
              new Getter(p -> p.getAuth().getConnectTimeout(), DURATION_MAPPER)),
          entry(
              "camunda.client.auth.read-timeout",
              new Getter(p -> p.getAuth().getReadTimeout(), DURATION_MAPPER)),
          entry(
              "camunda.client.worker.defaults.tenant-ids",
              new Getter(p -> p.getWorker().getDefaults().getTenantIds())),
          entry(
              "camunda.client.worker.defaults.tenant-filter-mode",
              new Getter(p -> p.getWorker().getDefaults().getTenantFilter().name())),
          entry(
              "camunda.client.worker.defaults.timeout",
              new Getter(p -> p.getWorker().getDefaults().getTimeout(), DURATION_MAPPER)),
          entry(
              "camunda.client.worker.defaults.max-jobs-active",
              new Getter(p -> p.getWorker().getDefaults().getMaxJobsActive())),
          entry(
              "camunda.client.worker.defaults.poll-interval",
              new Getter(p -> p.getWorker().getDefaults().getPollInterval(), DURATION_MAPPER)),
          entry(
              "camunda.client.worker.defaults.name",
              new Getter(p -> p.getWorker().getDefaults().getName())),
          entry(
              "camunda.client.worker.defaults.stream-enabled",
              new Getter(p -> p.getWorker().getDefaults().getStreamEnabled())),
          entry(
              "camunda.client.worker.defaults.enabled",
              new Getter(p -> p.getWorker().getDefaults().getEnabled())),
          entry(
              "camunda.client.worker.defaults.auto-complete",
              new Getter(p -> p.getWorker().getDefaults().getAutoComplete())),
          entry(
              "camunda.client.worker.defaults.request-timeout",
              new Getter(p -> p.getWorker().getDefaults().getRequestTimeout(), DURATION_MAPPER)),
          entry(
              "camunda.client.worker.defaults.max-retries",
              new Getter(p -> p.getWorker().getDefaults().getMaxRetries())),
          entry(
              "camunda.client.worker.defaults.force-fetch-all-variables",
              new Getter(p -> p.getWorker().getDefaults().getForceFetchAllVariables())),
          entry(
              "camunda.client.worker.defaults.stream-timeout",
              new Getter(p -> p.getWorker().getDefaults().getStreamTimeout(), DURATION_MAPPER)),
          entry(
              "camunda.client.max-http-connections",
              new Getter(CamundaClientProperties::getMaxHttpConnections)),
          entry(
              "camunda.client.worker.defaults.retry-backoff",
              new Getter(p -> p.getWorker().getDefaults().getRetryBackoff(), DURATION_MAPPER)));

  @Autowired CamundaClientProperties camundaClientProperties;

  /**
   * This test enforces the alignment between the additional properties defined in the metadata json
   * and the code
   */
  @TestFactory
  Stream<DynamicTest> alignmentWithDefaultPropertiesTest() throws IOException {
    final JsonNode jsonNode =
        MAPPER.readTree(
            ResourceUtils.getFile(
                "classpath:META-INF/additional-spring-configuration-metadata.json"));
    final ArrayNode properties = (ArrayNode) jsonNode.get("properties");
    return properties
        .valueStream()
        .filter(p -> p.has("defaultValue"))
        .map(
            p -> {
              final String name = p.get("name").asText();
              final JsonNode defaultValue = p.get("defaultValue");
              return DynamicTest.dynamicTest(
                  "Property " + name + " with default value " + defaultValue,
                  () -> {
                    assertThat(NEW_GETTERS).containsKey(name);
                    final Getter getter = NEW_GETTERS.get(name);
                    final Object value = getter.getter().apply(camundaClientProperties);
                    final Object transformedDefaultValue =
                        getter.defaultValueMapper().apply(defaultValue);
                    assertThat(value).isEqualTo(transformedDefaultValue);
                  });
            });
  }

  /**
   * This test ensures that all documented deprecations are also implemented in the legacy
   * properties mappings
   */
  @TestFactory
  Stream<DynamicTest> alignmentWithLegacyPropertyMappingsTest() throws IOException {
    final List<CamundaClientLegacyPropertiesMapping> legacyPropertiesMappings =
        CamundaClientLegacyPropertiesMappingsLoader.load();
    final JsonNode jsonNode =
        MAPPER.readTree(
            ResourceUtils.getFile(
                "classpath:META-INF/additional-spring-configuration-metadata.json"));
    final ArrayNode properties = (ArrayNode) jsonNode.get("properties");
    return properties
        .valueStream()
        .filter(p -> p.has("deprecation") && p.get("deprecation").has("replacement"))
        .map(
            p ->
                new DeprecatedProperty(
                    p.get("name").asText(), p.get("deprecation").get("replacement").asText()))
        .map(
            dp ->
                DynamicTest.dynamicTest(
                    dp.name() + " -> " + dp.replacement(),
                    () ->
                        assertThat(legacyPropertiesMappings)
                            .anyMatch(
                                m ->
                                    m.getPropertyName().equals(dp.replacement())
                                        && m.getLegacyPropertyNames().contains(dp.name()))));
  }

  private record DeprecatedProperty(String name, String replacement) {}

  private record Getter(
      Function<CamundaClientProperties, Object> getter,
      Function<JsonNode, Object> defaultValueMapper) {
    public Getter(final Function<CamundaClientProperties, Object> getter) {
      this(getter, o -> MAPPER.convertValue(o, Object.class));
    }
  }
}
