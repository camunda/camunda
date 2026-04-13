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
package io.camunda.client.spring.annotation.customizer;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.spring.configuration.CamundaBeanPostProcessorConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@SpringBootTest(
    classes = {
      CamundaBeanPostProcessorConfiguration.class,
      ZeebeWorkerValueCustomizerConfiguration.class
    })
@ExtendWith(OutputCaptureExtension.class)
public class JobWorkerValueCustomizerCompatTest {

  @Test
  void shouldLogDeprecationWarningForLegacyBean(final CapturedOutput capturedOutput) {
    assertThat(capturedOutput)
        .contains(
            "Bean 'testZeebeWorkerValueCustomizer' is implementing deprecated interface ZeebeWorkerValueCustomizer, please migrate to JobWorkerValueCustomizer");
  }
}
