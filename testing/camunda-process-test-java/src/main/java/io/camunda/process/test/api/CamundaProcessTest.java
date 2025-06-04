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

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a class as a process test and adds the JUnit extension {@link CamundaProcessTestExtension}.
 * Use {@link CamundaAssert} to verify the expected result of a test.
 *
 * <p>Example usage:
 *
 * <pre>
 * &#064;CamundaProcessTest
 * public class MyProcessTest {
 *
 *   // will be injected
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
 * or use the global container runtime. Essentially, as long as no properties of the {@see
 * CamundaProcessTestRuntimeBuilder} are changed from their defaults, the global runtime will be
 * preferred to reduce startup and teardown performance.
 *
 * <p>However, the global runtime can be disabled on a per-test-class basis or for the entire
 * project. To force a test class to create a local Camunda runtime, configure the extension using
 * `withLocalRuntime` as shown:
 *
 * <pre>
 *   @RegisterExtension
 *   private static final CamundaProcessTestExtension EXTENSION =
 *       new CamundaProcessTestExtension()
 *          .withLocalRuntime() // Will create a new test-class specific Camunda runtime
 * </pre>
 *
 * To disable the global container runtime, set the property
 * `camunda.process.test.globalRuntimeDisabled` to false. You can do this either by setting the
 * property in the maven POM:
 *
 * <pre>
 *   <properties>
 *     <io.camunda.process.test.globalRuntimeDisabled>false</io.camunda.process.test.globalRuntimeDisabled>
 *   </properties>
 * </pre>
 *
 * Or by configuring Maven with the flag `-Dio.camunda.process.test.globalRuntimeDisabled="false"`
 *
 * <p>Please note that disabling the global Camunda runtime may incur significant performance
 * penalties as every test class must start and stop its own runtime.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ExtendWith(CamundaProcessTestExtension.class)
public @interface CamundaProcessTest {}
