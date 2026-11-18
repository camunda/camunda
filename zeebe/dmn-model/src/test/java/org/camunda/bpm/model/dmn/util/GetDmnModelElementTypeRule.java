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
package org.camunda.bpm.model.dmn.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.test.GetModelElementTypeRule;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class GetDmnModelElementTypeRule extends TestWatcher implements GetModelElementTypeRule {

  private ModelInstance modelInstance;
  private Model model;
  private ModelElementType modelElementType;

  @Override
  @SuppressWarnings("unchecked")
  protected void starting(Description description) {
    String className = description.getClassName();
    assertThat(className).endsWith("Test");
    className = className.substring(0, className.length() - "Test".length());
    Class<? extends ModelElementInstance> instanceClass;
    try {
      instanceClass = (Class<? extends ModelElementInstance>) Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
    modelInstance = Dmn.createEmptyModel();
    model = modelInstance.getModel();
    modelElementType = model.getType(instanceClass);
  }

  public ModelInstance getModelInstance() {
    return modelInstance;
  }

  public Model getModel() {
    return model;
  }

  public ModelElementType getModelElementType() {
    return modelElementType;
  }
}
