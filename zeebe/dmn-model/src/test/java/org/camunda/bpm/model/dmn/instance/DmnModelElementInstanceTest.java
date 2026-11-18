/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.bpm.model.dmn.instance;

import org.camunda.bpm.model.dmn.impl.DmnModelConstants;
import org.camunda.bpm.model.dmn.util.GetDmnModelElementTypeRule;
import org.camunda.bpm.model.xml.test.AbstractModelElementInstanceTest;
import org.junit.BeforeClass;
import org.junit.ClassRule;

public abstract class DmnModelElementInstanceTest extends AbstractModelElementInstanceTest {

  @ClassRule
  public static final GetDmnModelElementTypeRule modelElementTypeRule = new GetDmnModelElementTypeRule();

  @BeforeClass
  public static void initModelElementType() {
    initModelElementType(modelElementTypeRule);
  }

  public String getDefaultNamespace() {
    return DmnModelConstants.LATEST_DMN_NS;
  }

}
