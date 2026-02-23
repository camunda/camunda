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
package io.camunda.client.jobhandling.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class AdHocSubprocessResultFunctionTest {
  @Test
  void shouldHaveNullVariables() {
    final AdHocSubProcessResultFunction resultFunction = r -> r.activateElement("test");
    assertThat(resultFunction.getVariables()).isNull();
  }

  @Test
  void shouldHaveVariables() {
    final Map<String, Object> variables = Map.of("foo", "bar");
    final AdHocSubProcessResultFunction resultFunction =
        AdHocSubProcessResultFunction.withVariables(variables, r -> r.activateElement("test"));
    assertThat(resultFunction.getVariables()).isEqualTo(Map.of("foo", "bar"));
  }
}
