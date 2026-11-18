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
package org.camunda.bpm.model.dmn.impl.instance;

import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.LATEST_DMN_NS;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ELEMENT_OUTPUT_VALUES;

import org.camunda.bpm.model.dmn.instance.OutputValues;
import org.camunda.bpm.model.dmn.instance.UnaryTests;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;

public class OutputValuesImpl extends UnaryTestsImpl implements OutputValues {

  public OutputValuesImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(ModelBuilder modelBuilder) {
    ModelElementTypeBuilder typeBuilder = modelBuilder.defineType(OutputValues.class, DMN_ELEMENT_OUTPUT_VALUES)
      .namespaceUri(LATEST_DMN_NS)
      .extendsType(UnaryTests.class)
      .instanceProvider(new ModelTypeInstanceProvider<OutputValues>() {
        public OutputValues newInstance(ModelTypeInstanceContext instanceContext) {
          return new OutputValuesImpl(instanceContext);
        }
      });

    typeBuilder.build();
  }

}
