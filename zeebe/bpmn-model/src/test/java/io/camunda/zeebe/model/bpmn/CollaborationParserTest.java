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

package io.camunda.zeebe.model.bpmn;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.model.bpmn.instance.Collaboration;
import io.camunda.zeebe.model.bpmn.instance.Conversation;
import io.camunda.zeebe.model.bpmn.instance.ConversationLink;
import io.camunda.zeebe.model.bpmn.instance.ConversationNode;
import io.camunda.zeebe.model.bpmn.instance.Event;
import io.camunda.zeebe.model.bpmn.instance.MessageFlow;
import io.camunda.zeebe.model.bpmn.instance.Participant;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import java.util.Collection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Sebastian Menski
 */
public class CollaborationParserTest {

  private static BpmnModelInstance modelInstance;
  private static Collaboration collaboration;

  @BeforeClass
  public static void parseModel() {
    modelInstance =
        Bpmn.readModelFromStream(
            CollaborationParserTest.class.getResourceAsStream("CollaborationParserTest.bpmn"));
    collaboration = modelInstance.getModelElementById("collaboration1");
  }

  @Test
  public void testConversations() {
    assertThat(collaboration.getConversationNodes()).hasSize(1);

    final ConversationNode conversationNode =
        collaboration.getConversationNodes().iterator().next();
    assertThat(conversationNode).isInstanceOf(Conversation.class);
    assertThat(conversationNode.getParticipants()).isEmpty();
    assertThat(conversationNode.getCorrelationKeys()).isEmpty();
    assertThat(conversationNode.getMessageFlows()).isEmpty();
  }

  @Test
  public void testConversationLink() {
    final Collection<ConversationLink> conversationLinks = collaboration.getConversationLinks();
    for (final ConversationLink conversationLink : conversationLinks) {
      assertThat(conversationLink.getId()).startsWith("conversationLink");
      assertThat(conversationLink.getSource()).isInstanceOf(Participant.class);
      final Participant source = (Participant) conversationLink.getSource();
      assertThat(source.getName()).isEqualTo("Pool");
      assertThat(source.getId()).startsWith("participant");

      assertThat(conversationLink.getTarget()).isInstanceOf(Conversation.class);
      final Conversation target = (Conversation) conversationLink.getTarget();
      assertThat(target.getId()).isEqualTo("conversation1");
    }
  }

  @Test
  public void testMessageFlow() {
    final Collection<MessageFlow> messageFlows = collaboration.getMessageFlows();
    for (final MessageFlow messageFlow : messageFlows) {
      assertThat(messageFlow.getId()).startsWith("messageFlow");
      assertThat(messageFlow.getSource()).isInstanceOf(ServiceTask.class);
      assertThat(messageFlow.getTarget()).isInstanceOf(Event.class);
    }
  }

  @Test
  public void testParticipant() {
    final Collection<Participant> participants = collaboration.getParticipants();
    for (final Participant participant : participants) {
      assertThat(participant.getProcess().getId()).startsWith("process");
    }
  }

  @Test
  public void testUnused() {
    assertThat(collaboration.getCorrelationKeys()).isEmpty();
    assertThat(collaboration.getArtifacts()).isEmpty();
    assertThat(collaboration.getConversationAssociations()).isEmpty();
    assertThat(collaboration.getMessageFlowAssociations()).isEmpty();
    assertThat(collaboration.getParticipantAssociations()).isEmpty();
  }

  @AfterClass
  public static void validateModel() {
    Bpmn.validateModel(modelInstance);
  }
}
