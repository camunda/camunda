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

import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.CAMUNDA_ATTRIBUTE_HISTORY_TIME_TO_LIVE;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.CAMUNDA_ATTRIBUTE_VERSION_TAG;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.CAMUNDA_NS;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.LATEST_DMN_NS;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ELEMENT_DECISION;

import java.util.Collection;

import org.camunda.bpm.model.dmn.instance.AllowedAnswers;
import org.camunda.bpm.model.dmn.instance.AuthorityRequirement;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.DecisionMakerReference;
import org.camunda.bpm.model.dmn.instance.DecisionOwnerReference;
import org.camunda.bpm.model.dmn.instance.DrgElement;
import org.camunda.bpm.model.dmn.instance.Expression;
import org.camunda.bpm.model.dmn.instance.ImpactedPerformanceIndicatorReference;
import org.camunda.bpm.model.dmn.instance.InformationRequirement;
import org.camunda.bpm.model.dmn.instance.KnowledgeRequirement;
import org.camunda.bpm.model.dmn.instance.OrganizationUnit;
import org.camunda.bpm.model.dmn.instance.PerformanceIndicator;
import org.camunda.bpm.model.dmn.instance.Question;
import org.camunda.bpm.model.dmn.instance.SupportedObjectiveReference;
import org.camunda.bpm.model.dmn.instance.UsingProcessReference;
import org.camunda.bpm.model.dmn.instance.UsingTaskReference;
import org.camunda.bpm.model.dmn.instance.Variable;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.ElementReferenceCollection;

public class DecisionImpl extends DrgElementImpl implements Decision {

  protected static ChildElement<Question> questionChild;
  protected static ChildElement<AllowedAnswers> allowedAnswersChild;
  protected static ChildElement<Variable> variableChild;
  protected static ChildElementCollection<InformationRequirement> informationRequirementCollection;
  protected static ChildElementCollection<KnowledgeRequirement> knowledgeRequirementCollection;
  protected static ChildElementCollection<AuthorityRequirement> authorityRequirementCollection;
  protected static ChildElementCollection<SupportedObjectiveReference> supportedObjectiveChildElementCollection;
  protected static ElementReferenceCollection<PerformanceIndicator, ImpactedPerformanceIndicatorReference> impactedPerformanceIndicatorRefCollection;
  protected static ElementReferenceCollection<OrganizationUnit, DecisionMakerReference> decisionMakerRefCollection;
  protected static ElementReferenceCollection<OrganizationUnit, DecisionOwnerReference> decisionOwnerRefCollection;
  protected static ChildElementCollection<UsingProcessReference> usingProcessCollection;
  protected static ChildElementCollection<UsingTaskReference> usingTaskCollection;
  protected static ChildElement<Expression> expressionChild;

  // camunda extensions
  protected static Attribute<String> camundaHistoryTimeToLiveAttribute;
  protected static Attribute<String> camundaVersionTag;

  public DecisionImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public Question getQuestion() {
    return questionChild.getChild(this);
  }

  public void setQuestion(Question question) {
    questionChild.setChild(this, question);
  }

  public AllowedAnswers getAllowedAnswers() {
    return allowedAnswersChild.getChild(this);
  }

  public void setAllowedAnswers(AllowedAnswers allowedAnswers) {
    allowedAnswersChild.setChild(this, allowedAnswers);
  }

  public Variable getVariable() {
    return variableChild.getChild(this);
  }

  public void setVariable(Variable variable) {
    variableChild.setChild(this, variable);
  }

  public Collection<InformationRequirement> getInformationRequirements() {
    return informationRequirementCollection.get(this);
  }

  public Collection<KnowledgeRequirement> getKnowledgeRequirements() {
    return knowledgeRequirementCollection.get(this);
  }

  public Collection<AuthorityRequirement> getAuthorityRequirements() {
    return authorityRequirementCollection.get(this);
  }

  public Collection<SupportedObjectiveReference> getSupportedObjectiveReferences() {
    return supportedObjectiveChildElementCollection.get(this);
  }

  public Collection<PerformanceIndicator> getImpactedPerformanceIndicators() {
    return impactedPerformanceIndicatorRefCollection.getReferenceTargetElements(this);
  }

  public Collection<OrganizationUnit> getDecisionMakers() {
    return decisionMakerRefCollection.getReferenceTargetElements(this);
  }

  public Collection<OrganizationUnit> getDecisionOwners() {
    return decisionOwnerRefCollection.getReferenceTargetElements(this);
  }

  public Collection<UsingProcessReference> getUsingProcessReferences() {
    return usingProcessCollection.get(this);
  }

  public Collection<UsingTaskReference> getUsingTaskReferences() {
    return usingTaskCollection.get(this);
  }

  public Expression getExpression() {
    return expressionChild.getChild(this);
  }

  public void setExpression(Expression expression) {
    expressionChild.setChild(this, expression);
  }

  // camunda extensions
  @Override
  public Integer getCamundaHistoryTimeToLive() {
    String ttl = getCamundaHistoryTimeToLiveString();

    if (ttl != null) {
      return Integer.valueOf(ttl);
    }
    return null;
  }

  @Override
  public void setCamundaHistoryTimeToLive(Integer historyTimeToLive) {
    setCamundaHistoryTimeToLiveString(String.valueOf(historyTimeToLive));
  }

  @Override
  public String getCamundaHistoryTimeToLiveString() {
    return camundaHistoryTimeToLiveAttribute.getValue(this);
  }

  @Override
  public void setCamundaHistoryTimeToLiveString(String historyTimeToLive) {
    camundaHistoryTimeToLiveAttribute.setValue(this, historyTimeToLive);
  }

  @Override
  public String getVersionTag() {
    return camundaVersionTag.getValue(this);
  }

  @Override
  public void setVersionTag(String inputVariable) {
    camundaVersionTag.setValue(this, inputVariable);
  }

  public static void registerType(ModelBuilder modelBuilder) {
    ModelElementTypeBuilder typeBuilder = modelBuilder.defineType(Decision.class, DMN_ELEMENT_DECISION)
      .namespaceUri(LATEST_DMN_NS)
      .extendsType(DrgElement.class)
      .instanceProvider(new ModelTypeInstanceProvider<Decision>() {
        public Decision newInstance(ModelTypeInstanceContext instanceContext) {
          return new DecisionImpl(instanceContext);
        }
      });

    SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    questionChild = sequenceBuilder.element(Question.class)
      .build();

    allowedAnswersChild = sequenceBuilder.element(AllowedAnswers.class)
      .build();

    variableChild = sequenceBuilder.element(Variable.class)
      .build();

    informationRequirementCollection = sequenceBuilder.elementCollection(InformationRequirement.class)
      .build();

    knowledgeRequirementCollection = sequenceBuilder.elementCollection(KnowledgeRequirement.class)
      .build();

    authorityRequirementCollection = sequenceBuilder.elementCollection(AuthorityRequirement.class)
      .build();

    supportedObjectiveChildElementCollection = sequenceBuilder.elementCollection(SupportedObjectiveReference.class)
      .build();

    impactedPerformanceIndicatorRefCollection = sequenceBuilder.elementCollection(ImpactedPerformanceIndicatorReference.class)
      .uriElementReferenceCollection(PerformanceIndicator.class)
      .build();

    decisionMakerRefCollection = sequenceBuilder.elementCollection(DecisionMakerReference.class)
      .uriElementReferenceCollection(OrganizationUnit.class)
      .build();

    decisionOwnerRefCollection = sequenceBuilder.elementCollection(DecisionOwnerReference.class)
      .uriElementReferenceCollection(OrganizationUnit.class)
      .build();

    usingProcessCollection = sequenceBuilder.elementCollection(UsingProcessReference.class)
      .build();

    usingTaskCollection = sequenceBuilder.elementCollection(UsingTaskReference.class)
      .build();

    expressionChild = sequenceBuilder.element(Expression.class)
      .build();

    // camunda extensions

    camundaHistoryTimeToLiveAttribute = typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_HISTORY_TIME_TO_LIVE)
        .namespace(CAMUNDA_NS)
        .build();

    camundaVersionTag = typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_VERSION_TAG)
      .namespace(CAMUNDA_NS)
      .build();

    typeBuilder.build();
  }

}
