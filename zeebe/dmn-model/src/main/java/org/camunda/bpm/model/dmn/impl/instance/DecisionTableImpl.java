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
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ATTRIBUTE_AGGREGATION;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ATTRIBUTE_HIT_POLICY;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ATTRIBUTE_OUTPUT_LABEL;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ATTRIBUTE_PREFERRED_ORIENTATION;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ELEMENT_DECISION_TABLE;

import java.util.Collection;

import org.camunda.bpm.model.dmn.BuiltinAggregator;
import org.camunda.bpm.model.dmn.DecisionTableOrientation;
import org.camunda.bpm.model.dmn.HitPolicy;
import org.camunda.bpm.model.dmn.instance.DecisionTable;
import org.camunda.bpm.model.dmn.instance.Expression;
import org.camunda.bpm.model.dmn.instance.Input;
import org.camunda.bpm.model.dmn.instance.Output;
import org.camunda.bpm.model.dmn.instance.Rule;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

public class DecisionTableImpl extends ExpressionImpl implements DecisionTable {
  
  protected static Attribute<HitPolicy> hitPolicyAttribute;
  protected static Attribute<BuiltinAggregator> aggregationAttribute;
  protected static Attribute<DecisionTableOrientation> preferredOrientationAttribute;
  protected static Attribute<String> outputLabelAttribute;

  protected static ChildElementCollection<Input> inputCollection;
  protected static ChildElementCollection<Output> outputCollection;
  protected static ChildElementCollection<Rule> ruleCollection;

  public DecisionTableImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public HitPolicy getHitPolicy() {
    return hitPolicyAttribute.getValue(this);
  }

  public void setHitPolicy(HitPolicy hitPolicy) {
    hitPolicyAttribute.setValue(this, hitPolicy);
  }

  public BuiltinAggregator getAggregation() {
    return aggregationAttribute.getValue(this);
  }

  public void setAggregation(BuiltinAggregator aggregation) {
    aggregationAttribute.setValue(this, aggregation);
  }

  public DecisionTableOrientation getPreferredOrientation() {
    return preferredOrientationAttribute.getValue(this);
  }

  public void setPreferredOrientation(DecisionTableOrientation preferredOrientation) {
    preferredOrientationAttribute.setValue(this, preferredOrientation);
  }

  public String getOutputLabel() {
    return outputLabelAttribute.getValue(this);
  }

  public void setOutputLabel(String outputLabel) {
    outputLabelAttribute.setValue(this, outputLabel);
  }

  public Collection<Input> getInputs() {
    return inputCollection.get(this);
  }

  public Collection<Output> getOutputs() {
    return outputCollection.get(this);
  }

  public Collection<Rule> getRules() {
    return ruleCollection.get(this);
  }

  public static void registerType(ModelBuilder modelBuilder) {
    ModelElementTypeBuilder typeBuilder = modelBuilder.defineType(DecisionTable.class, DMN_ELEMENT_DECISION_TABLE)
      .namespaceUri(LATEST_DMN_NS)
      .extendsType(Expression.class)
      .instanceProvider(new ModelTypeInstanceProvider<DecisionTable>() {
        public DecisionTable newInstance(ModelTypeInstanceContext instanceContext) {
          return new DecisionTableImpl(instanceContext);
        }
      });

    hitPolicyAttribute = typeBuilder.namedEnumAttribute(DMN_ATTRIBUTE_HIT_POLICY, HitPolicy.class)
      .defaultValue(HitPolicy.UNIQUE)
      .build();

    aggregationAttribute = typeBuilder.enumAttribute(DMN_ATTRIBUTE_AGGREGATION, BuiltinAggregator.class)
      .build();

    preferredOrientationAttribute = typeBuilder.namedEnumAttribute(DMN_ATTRIBUTE_PREFERRED_ORIENTATION, DecisionTableOrientation.class)
      .defaultValue(DecisionTableOrientation.Rule_as_Row)
      .build();

    outputLabelAttribute = typeBuilder.stringAttribute(DMN_ATTRIBUTE_OUTPUT_LABEL)
      .build();

    SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    inputCollection = sequenceBuilder.elementCollection(Input.class)
      .build();

    outputCollection = sequenceBuilder.elementCollection(Output.class)
      .required()
      .build();

    ruleCollection = sequenceBuilder.elementCollection(Rule.class)
      .build();

    typeBuilder.build();
  }

}
