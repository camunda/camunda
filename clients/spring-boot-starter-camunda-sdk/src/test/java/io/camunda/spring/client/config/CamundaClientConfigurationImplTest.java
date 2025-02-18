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
package io.camunda.spring.client.config;

import static org.assertj.core.api.Assertions.*;

<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/spring/client/config/CamundaClientConfigurationImplTest.java
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.impl.NoopCredentialsProvider;
import io.camunda.spring.client.configuration.CamundaClientConfigurationImpl;
import io.camunda.spring.client.jobhandling.CamundaClientExecutorService;
import io.camunda.spring.client.properties.CamundaClientProperties;
=======
import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.impl.NoopCredentialsProvider;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientConfigurationImpl;
import io.camunda.zeebe.spring.client.jobhandling.ZeebeClientExecutorService;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/zeebe/spring/client/config/ZeebeClientConfigurationImplTest.java
import io.grpc.ClientInterceptor;
import java.util.List;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.junit.jupiter.api.Test;

<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/spring/client/config/CamundaClientConfigurationImplTest.java
public class CamundaClientConfigurationImplTest {
  private static CamundaClientConfigurationImpl configuration(
=======
public class ZeebeClientConfigurationImplTest {
  private static ZeebeClientConfigurationImpl configuration(
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/zeebe/spring/client/config/ZeebeClientConfigurationImplTest.java
      final CamundaClientProperties properties,
      final JsonMapper jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/spring/client/config/CamundaClientConfigurationImplTest.java
      final CamundaClientExecutorService executorService,
      final CredentialsProvider credentialsProvider) {
    return new CamundaClientConfigurationImpl(
=======
      final ZeebeClientExecutorService executorService,
      final CredentialsProvider credentialsProvider) {
    return new ZeebeClientConfigurationImpl(
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/zeebe/spring/client/config/ZeebeClientConfigurationImplTest.java
        properties, jsonMapper, interceptors, chainHandlers, executorService, credentialsProvider);
  }

  private static CamundaClientProperties properties() {
    return new CamundaClientProperties();
  }

  private static JsonMapper jsonMapper() {
    return new CamundaObjectMapper();
  }

  private static CamundaClientExecutorService executorService() {
    return CamundaClientExecutorService.createDefault();
  }

  private static CredentialsProvider credentialsProvider() {
    return new NoopCredentialsProvider();
  }

  private static CredentialsProvider credentialsProvider() {
    return new NoopCredentialsProvider();
  }

  @Test
  void shouldCreateSingletonCredentialProvider() {
    final CamundaClientConfigurationImpl configuration =
        configuration(
            properties(),
            jsonMapper(),
            List.of(),
            List.of(),
            executorService(),
            credentialsProvider());
    final CredentialsProvider credentialsProvider1 = configuration.getCredentialsProvider();
    final CredentialsProvider credentialsProvider2 = configuration.getCredentialsProvider();
    assertThat(credentialsProvider1).isSameAs(credentialsProvider2);
  }
}
