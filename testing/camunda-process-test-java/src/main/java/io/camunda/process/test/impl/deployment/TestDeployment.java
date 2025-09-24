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
package io.camunda.process.test.impl.deployment;

import io.camunda.process.test.api.CamundaProcessTest;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to define deployment resources for Camunda process tests. Resources are deployed
 * before the test method execution.
 *
 * <p>Example usage:
 *
 * <pre>
 * &#064;TestDeployment(resources = "my-test-process.bpmn")
 * &#064;Test
 * void shouldWork() {
 *   // Process is already deployed and available
 * }
 *
 * &#064;TestDeployment(resources = {"process1.bpmn", "process2.bpmn", "decision.dmn"})
 * &#064;Test
 * void shouldWorkWithMultipleResources() {
 *   // Multiple resources are deployed
 * }
 * </pre>
 *
 * @see CamundaProcessTest
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TestDeployment {

  /**
   * The deployment resources (BPMN, DMN, etc.) to be deployed before test execution. Resources are
   * loaded from the classpath.
   *
   * @return array of resource paths
   */
  String[] resources() default {};
}
