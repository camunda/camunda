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
package io.camunda.process.test.impl.spec.dsl;

import io.camunda.process.test.api.spec.CamundaProcessSpecTestCase;
import java.util.List;

public class SpecTestCase implements CamundaProcessSpecTestCase {

  private String name;
  private List<SpecInstruction> instructions;

  public SpecTestCase() {}

  public SpecTestCase(final String name, final List<SpecInstruction> instructions) {
    this.name = name;
    this.instructions = instructions;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public List<SpecInstruction> getInstructions() {
    return instructions;
  }

  public void setInstructions(final List<SpecInstruction> instructions) {
    this.instructions = instructions;
  }

  @Override
  public String toString() {
    return name;
  }
}
