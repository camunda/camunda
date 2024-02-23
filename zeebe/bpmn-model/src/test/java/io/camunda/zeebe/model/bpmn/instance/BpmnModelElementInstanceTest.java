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

package io.camunda.zeebe.model.bpmn.instance;

import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.test.AbstractModelElementInstanceTest;
import io.camunda.zeebe.model.bpmn.util.GetBpmnModelElementTypeRule;
import org.junit.BeforeClass;
import org.junit.ClassRule;

/**
 * @author Sebastian Menski
 */
public abstract class BpmnModelElementInstanceTest extends AbstractModelElementInstanceTest {

  @ClassRule
  public static final GetBpmnModelElementTypeRule MODEL_ELEMENT_TYPE_RULE =
      new GetBpmnModelElementTypeRule();

  @BeforeClass
  public static void initModelElementType() {
    initModelElementType(MODEL_ELEMENT_TYPE_RULE);
  }

  @Override
  public String getDefaultNamespace() {
    return BpmnModelConstants.BPMN20_NS;
  }
}
