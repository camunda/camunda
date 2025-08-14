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
import io.camunda.process.test.impl.configuration.CamundaProcessTestEmbeddedRuntimeConfiguration;
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
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
// this pulls in the Configuration NOT as AutoConfiguration but directly creates beans, so the
// marker is present when the normal CamundaAutoConfiguration is used by the normal
// meta-inf/services way
@Import({
  CamundaProcessTestAutoConfiguration.class,
  CamundaProcessTestEmbeddedRuntimeConfiguration.class
})
// this listener hooks up into test execution
@TestExecutionListeners(
    listeners = CamundaProcessTestExecutionListener.class,
    mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
public @interface CamundaEmbeddedSpringProcessTest {}
