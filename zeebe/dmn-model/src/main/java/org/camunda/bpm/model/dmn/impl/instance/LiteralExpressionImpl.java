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
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ATTRIBUTE_EXPRESSION_LANGUAGE;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ELEMENT_LITERAL_EXPRESSION;

import org.camunda.bpm.model.dmn.instance.Expression;
import org.camunda.bpm.model.dmn.instance.ImportedValues;
import org.camunda.bpm.model.dmn.instance.LiteralExpression;
import org.camunda.bpm.model.dmn.instance.Text;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

public class LiteralExpressionImpl extends ExpressionImpl implements LiteralExpression {

  protected static Attribute<String> expressionLanguageAttribute;

  protected static ChildElement<Text> textChild;
  protected static ChildElement<ImportedValues> importedValuesChild;

  public LiteralExpressionImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public String getExpressionLanguage() {
    return expressionLanguageAttribute.getValue(this);
  }

  public void setExpressionLanguage(String expressionLanguage) {
    expressionLanguageAttribute.setValue(this, expressionLanguage);
  }

  public Text getText() {
    return textChild.getChild(this);
  }

  public void setText(Text text) {
    textChild.setChild(this, text);
  }

  public ImportedValues getImportValues() {
    return importedValuesChild.getChild(this);
  }

  public void setImportValues(ImportedValues importedValues) {
    importedValuesChild.setChild(this, importedValues);
  }

  public static void registerType(ModelBuilder modelBuilder) {
    ModelElementTypeBuilder typeBuilder = modelBuilder.defineType(LiteralExpression.class, DMN_ELEMENT_LITERAL_EXPRESSION)
      .namespaceUri(LATEST_DMN_NS)
      .extendsType(Expression.class)
      .instanceProvider(new ModelTypeInstanceProvider<LiteralExpression>() {
        public LiteralExpression newInstance(ModelTypeInstanceContext instanceContext) {
          return new LiteralExpressionImpl(instanceContext);
        }
      });

    expressionLanguageAttribute = typeBuilder.stringAttribute(DMN_ATTRIBUTE_EXPRESSION_LANGUAGE)
      .build();

    SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    textChild = sequenceBuilder.element(Text.class)
      .build();

    importedValuesChild = sequenceBuilder.element(ImportedValues.class)
      .build();

    typeBuilder.build();
  }

}
