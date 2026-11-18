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
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ELEMENT_DECISION_SERVICE;

import java.util.Collection;

import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.DecisionService;
import org.camunda.bpm.model.dmn.instance.EncapsulatedDecisionReference;
import org.camunda.bpm.model.dmn.instance.InputData;
import org.camunda.bpm.model.dmn.instance.InputDataReference;
import org.camunda.bpm.model.dmn.instance.InputDecisionReference;
import org.camunda.bpm.model.dmn.instance.NamedElement;
import org.camunda.bpm.model.dmn.instance.OutputDecisionReference;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.ElementReferenceCollection;

public class DecisionServiceImpl extends NamedElementImpl implements DecisionService {

  protected static ElementReferenceCollection<Decision, OutputDecisionReference> outputDecisionRefCollection;
  protected static ElementReferenceCollection<Decision, EncapsulatedDecisionReference> encapsulatedDecisionRefCollection;
  protected static ElementReferenceCollection<Decision, InputDecisionReference> inputDecisionRefCollection;
  protected static ElementReferenceCollection<InputData, InputDataReference> inputDataRefCollection;

  public DecisionServiceImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public Collection<Decision> getOutputDecisions() {
    return outputDecisionRefCollection.getReferenceTargetElements(this);
  }

  public Collection<Decision> getEncapsulatedDecisions() {
    return encapsulatedDecisionRefCollection.getReferenceTargetElements(this);
  }

  public Collection<Decision> getInputDecisions() {
    return inputDecisionRefCollection.getReferenceTargetElements(this);
  }

  public Collection<InputData> getInputData() {
    return inputDataRefCollection.getReferenceTargetElements(this);
  }

  public static void registerType(ModelBuilder modelBuilder) {
    ModelElementTypeBuilder typeBuilder = modelBuilder.defineType(DecisionService.class, DMN_ELEMENT_DECISION_SERVICE)
      .namespaceUri(LATEST_DMN_NS)
      .extendsType(NamedElement.class)
      .instanceProvider(new ModelTypeInstanceProvider<DecisionService>() {
        public DecisionService newInstance(ModelTypeInstanceContext instanceContext) {
          return new DecisionServiceImpl(instanceContext);
        }
      });

    SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    outputDecisionRefCollection = sequenceBuilder.elementCollection(OutputDecisionReference.class)
      .required()
      .uriElementReferenceCollection(Decision.class)
      .build();

    encapsulatedDecisionRefCollection = sequenceBuilder.elementCollection(EncapsulatedDecisionReference.class)
      .uriElementReferenceCollection(Decision.class)
      .build();

    inputDecisionRefCollection = sequenceBuilder.elementCollection(InputDecisionReference.class)
      .uriElementReferenceCollection(Decision.class)
      .build();

    inputDataRefCollection = sequenceBuilder.elementCollection(InputDataReference.class)
      .uriElementReferenceCollection(InputData.class)
      .build();

    typeBuilder.build();
  }

}
