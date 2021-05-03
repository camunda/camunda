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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_SUB_CONVERSATION;

import io.zeebe.model.bpmn.instance.ConversationNode;
import io.zeebe.model.bpmn.instance.SubConversation;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

/**
 * The BPMN subConversation element
 *
 * @author Sebastian Menski
 */
public class SubConversationImpl extends ConversationNodeImpl implements SubConversation {

  protected static ChildElementCollection<ConversationNode> conversationNodeCollection;

  public SubConversationImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(SubConversation.class, BPMN_ELEMENT_SUB_CONVERSATION)
            .namespaceUri(BPMN20_NS)
            .extendsType(ConversationNode.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<SubConversation>() {
                  @Override
                  public SubConversation newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new SubConversationImpl(instanceContext);
                  }
                });

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    conversationNodeCollection = sequenceBuilder.elementCollection(ConversationNode.class).build();

    typeBuilder.build();
  }

  @Override
  public Collection<ConversationNode> getConversationNodes() {
    return conversationNodeCollection.get(this);
  }
}
