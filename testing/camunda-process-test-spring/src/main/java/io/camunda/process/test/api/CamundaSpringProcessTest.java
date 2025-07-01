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
package io.camunda.process.test.api;

import io.camunda.process.test.impl.configuration.CamundaProcessTestAutoConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;

/**
 * Marks a class as a process test and adds the Spring test execution listener {@link
 * CamundaProcessTestExecutionListener}. Use {@link CamundaAssert} to verify the expected result of
 * a test.
 *
 * <p>Example usage:
 *
 * <pre>
 * &#064;CamundaSpringProcessTest
 * public class MyProcessTest {
 *
 *   &#064;Autowired
 *   private ZeebeClient client;
 *
 *   &#064;Test
 *   void shouldWork() {
 *     // given
 *     final ProcessInstanceEvent processInstance =
 *         client
 *             .newCreateInstanceCommand()
 *             .bpmnProcessId("process")
 *             .latestVersion()
 *             .send()
 *             .join();
 *
 *     // when
 *
 *     // then
 *     CamundaAssert.assertThat(processInstance)
 *         .isCompleted()
 *         .hasCompletedElements("A", "B");
 *   }
 * }
 * </pre>
 *
 * <p>Depending on a test class' configuration, the CPT may create a local Camunda container runtime
 * or use the global container runtime. You may configure the global runtime in your Spring
 * application configuration ({@see CamundaProcessTestGlobalRuntimeConfiguration} for more
 * information) like so:
 *
 * <pre>
 *   io:
 *     camunda:
 *       process:
 *         test:
 *           global:
 *             camunda-docker-image-name: "custom-image"
 *             connectors-enabled: true
 * </pre>
 *
 * Then, whenever a new CPT test class is initialized, the test's configuration is compared with
 * that of the global runtime. If they're compatible, the global runtime is preferred over creating
 * a new one.
 *
 * <p>However, the global runtime can be ignored on a per-class basis or disabled entirely. To force
 * the extension to create a new Camunda runtime, regardless of its configuration, set the
 * `ignore-global-runtime` property to `true`:
 *
 * <pre>
 *   @SpringBootTest(
 *     classes = {CamundaSpringProcessTestConnectorsIT.class},
 *     properties = {
 *       "io.camunda.process.test.ignore-global-runtime=true"
 *     })
 * </pre>
 *
 * You can also disable the global runtime. All test classes will then always create a new runtime.
 * You can set the flag in your Maven POM, as a Maven flag, or by editing the
 * `camunda-container-runtime.properties` file:
 *
 * <p>Editing the Maven POM:
 *
 * <pre>
 *   <properties>
 *     <io.camunda.process.test.globalRuntimeEnabled>false</io.camunda.process.test.globalRuntimeEnabled>
 *   </properties>
 * </pre>
 *
 * Adding a Maven flag:
 *
 * <pre>
 *    -Dio.camunda.process.test.globalRuntimeEnabled="false"
 * </pre>
 *
 * Changing the Camunda Container Runtime properties file:
 *
 * <pre>
 *   camunda.process.test.globalRuntimeEnabled=false
 * </pre>
 *
 * <p>Please note that disabling the global Camunda runtime may incur significant performance
 * penalties as every test class must start and stop its own runtime.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
// this pulls in the Configuration NOT as AutoConfiguration but directly creates beans, so the
// marker is present when the normal CamundaAutoConfiguration is used by the normal
// meta-inf/services way
@Import({CamundaProcessTestAutoConfiguration.class})
// this listener hooks up into test execution
@TestExecutionListeners(
    listeners = CamundaProcessTestExecutionListener.class,
    mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
public @interface CamundaSpringProcessTest {}
