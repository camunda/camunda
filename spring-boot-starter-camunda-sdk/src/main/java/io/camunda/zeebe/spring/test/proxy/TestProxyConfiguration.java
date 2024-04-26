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
package io.camunda.zeebe.spring.test.proxy;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Proxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

public class TestProxyConfiguration {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Bean
  public ZeebeClientProxy zeebeClientProxy() {
    return new ZeebeClientProxy();
  }

  @Bean
  @Primary
  public ZeebeClient proxiedZeebeClient(final ZeebeClientProxy zeebeClientProxy) {
    return (ZeebeClient)
        Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class[] {ZeebeClient.class}, zeebeClientProxy);
  }

  @Bean
  public ZeebeTestEngineProxy zeebeTestEngineProxy() {
    return new ZeebeTestEngineProxy();
  }

  @Bean
  public ZeebeTestEngine proxiedZeebeTestEngine(final ZeebeTestEngineProxy zeebeTestEngineProxy) {
    return (ZeebeTestEngine)
        Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class[] {ZeebeTestEngine.class}, zeebeTestEngineProxy);
  }
}
