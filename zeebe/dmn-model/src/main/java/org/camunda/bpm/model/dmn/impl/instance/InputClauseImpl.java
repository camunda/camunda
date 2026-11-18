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

import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.CAMUNDA_ATTRIBUTE_INPUT_VARIABLE;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.CAMUNDA_NS;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.LATEST_DMN_NS;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ELEMENT_INPUT_CLAUSE;

import org.camunda.bpm.model.dmn.instance.DmnElement;
import org.camunda.bpm.model.dmn.instance.InputClause;
import org.camunda.bpm.model.dmn.instance.InputExpression;
import org.camunda.bpm.model.dmn.instance.InputValues;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

public class InputClauseImpl extends DmnElementImpl implements InputClause {

  protected static ChildElement<InputExpression> inputExpressionChild;
  protected static ChildElement<InputValues> inputValuesChild;

  // camunda extensions
  protected static Attribute<String> camundaInputVariableAttribute;

  public InputClauseImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public InputExpression getInputExpression() {
    return inputExpressionChild.getChild(this);
  }

  public void setInputExpression(InputExpression inputExpression) {
    inputExpressionChild.setChild(this, inputExpression);
  }

  public InputValues getInputValues() {
    return inputValuesChild.getChild(this);
  }

  public void setInputValues(InputValues inputValues) {
    inputValuesChild.setChild(this, inputValues);
  }

  // camunda extensions

  public String getCamundaInputVariable() {
    return camundaInputVariableAttribute.getValue(this);
  }


  public void setCamundaInputVariable(String inputVariable) {
    camundaInputVariableAttribute.setValue(this, inputVariable);
  }

  public static void registerType(ModelBuilder modelBuilder) {
    ModelElementTypeBuilder typeBuilder = modelBuilder.defineType(InputClause.class, DMN_ELEMENT_INPUT_CLAUSE)
      .namespaceUri(LATEST_DMN_NS)
      .extendsType(DmnElement.class)
      .instanceProvider(new ModelTypeInstanceProvider<InputClause>() {
        public InputClause newInstance(ModelTypeInstanceContext instanceContext) {
          return new InputClauseImpl(instanceContext);
        }
      });

    SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    inputExpressionChild = sequenceBuilder.element(InputExpression.class)
      .required()
      .build();

    inputValuesChild = sequenceBuilder.element(InputValues.class)
      .build();

    // camunda extensions

    camundaInputVariableAttribute = typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_INPUT_VARIABLE)
      .namespace(CAMUNDA_NS)
      .build();

    typeBuilder.build();
  }

}
