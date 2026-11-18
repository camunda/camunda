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
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ELEMENT_FUNCTION_DEFINITION;

import java.util.Collection;

import org.camunda.bpm.model.dmn.instance.Expression;
import org.camunda.bpm.model.dmn.instance.FormalParameter;
import org.camunda.bpm.model.dmn.instance.FunctionDefinition;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

public class FunctionDefinitionImpl extends ExpressionImpl implements FunctionDefinition {

  protected static ChildElementCollection<FormalParameter> formalParameterCollection;
  protected static ChildElement<Expression> expressionChild;

  public FunctionDefinitionImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public Collection<FormalParameter> getFormalParameters() {
    return formalParameterCollection.get(this);
  }

  public Expression getExpression() {
    return expressionChild.getChild(this);
  }

  public void setExpression(Expression expression) {
    expressionChild.setChild(this, expression);
  }

  public static void registerType(ModelBuilder modelBuilder) {
    ModelElementTypeBuilder typeBuilder = modelBuilder.defineType(FunctionDefinition.class, DMN_ELEMENT_FUNCTION_DEFINITION)
      .namespaceUri(LATEST_DMN_NS)
      .extendsType(Expression.class)
      .instanceProvider(new ModelTypeInstanceProvider<FunctionDefinition>() {
        public FunctionDefinition newInstance(ModelTypeInstanceContext instanceContext) {
          return new FunctionDefinitionImpl(instanceContext);
        }
      });

    SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    formalParameterCollection = sequenceBuilder.elementCollection(FormalParameter.class)
      .build();

    expressionChild = sequenceBuilder.element(Expression.class)
      .build();

    typeBuilder.build();
  }

}
