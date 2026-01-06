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
package io.camunda.process.test.impl.configuration;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.dsl.TestScenarioRunner;
import io.camunda.process.test.impl.proxy.CamundaClientProxy;
import io.camunda.process.test.impl.proxy.CamundaProcessTestContextProxy;
import io.camunda.process.test.impl.proxy.TestScenarioRunnerProxy;
import io.camunda.process.test.impl.proxy.ZeebeClientProxy;
import io.camunda.zeebe.client.ZeebeClient;
import java.lang.reflect.Proxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

public class CamundaProcessTestProxyConfiguration {

  @Bean
  public CamundaClientProxy camundaClientProxy() {
    return new CamundaClientProxy();
  }

  @Bean(destroyMethod = "")
  @Primary
  public CamundaClient proxiedCamundaClient(final CamundaClientProxy camundaClientProxy) {
    return (CamundaClient)
        Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class[] {CamundaClient.class}, camundaClientProxy);
  }

  @Bean
  public ZeebeClientProxy zeebeClientProxy() {
    return new ZeebeClientProxy();
  }

  @Bean(destroyMethod = "")
  @Primary
  public ZeebeClient proxiedZeebeClient(final ZeebeClientProxy zeebeClientProxy) {
    return (ZeebeClient)
        Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class[] {ZeebeClient.class}, zeebeClientProxy);
  }

  @Bean
  public CamundaProcessTestContextProxy camundaProcessTestContextProxy() {
    return new CamundaProcessTestContextProxy();
  }

  @Bean
  public CamundaProcessTestContext proxiedCamundaProcessTestContext(
      final CamundaProcessTestContextProxy camundaProcessTestContextProxy) {
    return (CamundaProcessTestContext)
        Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[] {CamundaProcessTestContext.class},
            camundaProcessTestContextProxy);
  }

  @Bean
  public TestScenarioRunnerProxy testScenarioRunnerProxy() {
    return new TestScenarioRunnerProxy();
  }

  @Bean
  public TestScenarioRunner proxiedTestScenarioRunner(
      final TestScenarioRunnerProxy testScenarioRunnerProxy) {
    return (TestScenarioRunner)
        Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[] {TestScenarioRunner.class},
            testScenarioRunnerProxy);
  }
}
