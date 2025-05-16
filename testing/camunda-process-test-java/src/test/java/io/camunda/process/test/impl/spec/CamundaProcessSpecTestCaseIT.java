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
package io.camunda.process.test.impl.spec;

import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.spec.CamundaProcessSpecResource;
import io.camunda.process.test.api.spec.CamundaProcessSpecRunner;
import io.camunda.process.test.api.spec.CamundaProcessSpecSource;
import io.camunda.process.test.api.spec.CamundaProcessSpecTestCase;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;

@CamundaProcessTest
public class CamundaProcessSpecTestCaseIT {

  private CamundaProcessSpecRunner processSpecRunner;

  @ParameterizedTest
  @CamundaProcessSpecSource(specDirectory = "/specs")
  void shouldPassTestCase(
      final CamundaProcessSpecTestCase testCase, final List<CamundaProcessSpecResource> resources) {
    // given - (optional) set up mocks, job workers, etc.

    // when - run and verify the test case
    processSpecRunner.runTestCase(testCase, resources);

    // then - (optional) verify mock invocations, external resources, etc.
  }
}
