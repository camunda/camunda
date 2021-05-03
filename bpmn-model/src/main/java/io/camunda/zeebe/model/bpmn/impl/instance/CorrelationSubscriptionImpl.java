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

package io.zeebe.model.bpmn.impl.instance;

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN20_NS;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_CORRELATION_KEY_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_CORRELATION_SUBSCRIPTION;

import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.CorrelationKey;
import io.zeebe.model.bpmn.instance.CorrelationPropertyBinding;
import io.zeebe.model.bpmn.instance.CorrelationSubscription;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

/**
 * The BPMN correlationSubscription element
 *
 * @author Sebastian Menski
 */
public class CorrelationSubscriptionImpl extends BaseElementImpl
    implements CorrelationSubscription {

  protected static AttributeReference<CorrelationKey> correlationKeyAttribute;
  protected static ChildElementCollection<CorrelationPropertyBinding>
      correlationPropertyBindingCollection;

  public CorrelationSubscriptionImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(CorrelationSubscription.class, BPMN_ELEMENT_CORRELATION_SUBSCRIPTION)
            .namespaceUri(BPMN20_NS)
            .extendsType(BaseElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<CorrelationSubscription>() {
                  @Override
                  public CorrelationSubscription newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new CorrelationSubscriptionImpl(instanceContext);
                  }
                });

    correlationKeyAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_CORRELATION_KEY_REF)
            .required()
            .qNameAttributeReference(CorrelationKey.class)
            .build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    correlationPropertyBindingCollection =
        sequenceBuilder.elementCollection(CorrelationPropertyBinding.class).build();

    typeBuilder.build();
  }

  @Override
  public CorrelationKey getCorrelationKey() {
    return correlationKeyAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setCorrelationKey(final CorrelationKey correlationKey) {
    correlationKeyAttribute.setReferenceTargetElement(this, correlationKey);
  }

  @Override
  public Collection<CorrelationPropertyBinding> getCorrelationPropertyBindings() {
    return correlationPropertyBindingCollection.get(this);
  }
}
