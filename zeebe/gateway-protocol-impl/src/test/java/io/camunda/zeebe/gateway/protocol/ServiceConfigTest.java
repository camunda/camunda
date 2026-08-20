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
package io.camunda.zeebe.gateway.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.MethodDescriptor;
import io.grpc.ServiceDescriptor;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.assertj.core.api.Condition;
import org.assertj.core.condition.VerboseCondition;
import org.junit.jupiter.api.Test;

final class ServiceConfigTest {

  /**
   * Verifies that every method/RPC of the Gateway service (as defined in gateway.proto) has a
   * corresponding retry policy defined in gateway-service-config.json (see this module's
   * resources).
   *
   * <p>If one is missing, you will need to either add the method to one of the existing policies,
   * or if none of the existing policies match, create a new policy for this method.
   *
   * <p>See <a
   * href="https://github.com/grpc/grpc/blob/master/doc/service_config.md">service_config.md</a> for
   * more about the syntax.
   */
  @Test
  void shouldHaveAPolicyForAllServiceMethods() throws IOException {
    // given
    final ObjectMapper objectMapper = new ObjectMapper();
    final ServiceDescriptor serviceDescriptor = GatewayGrpc.getServiceDescriptor();
    final Collection<MethodDescriptor<?, ?>> methods = serviceDescriptor.getMethods();
    final URL configUrl =
        ClassLoader.getSystemClassLoader().getResource("gateway-service-config.json");
    final PartialServiceConfig serviceConfig =
        objectMapper.readValue(configUrl, PartialServiceConfig.class);

    // when - then
    for (final MethodDescriptor<?, ?> method : methods) {
      assertThat(serviceConfig).has(hasRetryPolicyFor(method, configUrl));
    }
  }

  private Condition<? super PartialServiceConfig> hasRetryPolicyFor(
      final MethodDescriptor<?, ?> method, final URL configUrl) {
    final MethodName expected = new MethodName(method.getServiceName(), method.getBareMethodName());
    return VerboseCondition.verboseCondition(
        config ->
            config.methodConfig.stream()
                .filter(m -> m.name.contains(expected))
                .anyMatch(m -> m.retryPolicy != null),
        String.format(
            "a service config with a retry policy for method '%s' defined",
            method.getFullMethodName()),
        config -> String.format(" but no such retry policy was found in '%s'", configUrl));
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class PartialMethodConfig {
    private final List<MethodName> name;
    private final Object retryPolicy;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public PartialMethodConfig(
        final @JsonProperty("name") List<MethodName> name,
        final @JsonProperty("retryPolicy") Object retryPolicy) {
      this.name = name;
      this.retryPolicy = retryPolicy;
    }

    @Override
    public String toString() {
      return "PartialMethodConfig{" + "name=" + name + ", retryPolicy=" + retryPolicy + '}';
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class MethodName {
    private final String service;
    private final String method;

    @JsonCreator(mode = Mode.PROPERTIES)
    public MethodName(
        final @JsonProperty("service") String service,
        final @JsonProperty("method") String method) {
      this.service = service;
      this.method = method;
    }

    @Override
    public int hashCode() {
      return Objects.hash(service, method);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }

      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final MethodName that = (MethodName) o;
      return Objects.equals(service, that.service) && Objects.equals(method, that.method);
    }

    @Override
    public String toString() {
      return "MethodName{" + "service='" + service + '\'' + ", method='" + method + '\'' + '}';
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class PartialServiceConfig {
    private final List<PartialMethodConfig> methodConfig;

    @JsonCreator
    public PartialServiceConfig(
        final @JsonProperty("methodConfig") List<PartialMethodConfig> methodConfig) {
      this.methodConfig = methodConfig;
    }

    @Override
    public String toString() {
      return "PartialServiceConfig{" + "methodConfig=" + methodConfig + '}';
    }
  }
}
