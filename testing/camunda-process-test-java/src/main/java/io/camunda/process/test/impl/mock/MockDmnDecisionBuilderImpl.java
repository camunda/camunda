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
package io.camunda.process.test.impl.mock;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.mock.MockDmnDecisionBuilder;
import java.io.ByteArrayInputStream;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.Definitions;
import org.camunda.bpm.model.dmn.instance.ExtensionElements;
import org.camunda.bpm.model.dmn.instance.LiteralExpression;
import org.camunda.bpm.model.dmn.instance.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockDmnDecisionBuilderImpl implements MockDmnDecisionBuilder {

  private static final String ZEEBE_NAMESPACE = "http://camunda.org/schema/zeebe/1.0";
  private static final Logger LOGGER = LoggerFactory.getLogger(MockDmnDecisionBuilderImpl.class);

  private final CamundaClient client;
  private String decisionId;
  private String versionTag;

  public MockDmnDecisionBuilderImpl(final CamundaClient client) {
    this.client = client;
  }

  @Override
  public MockDmnDecisionBuilder withDecisionId(final String decisionId) {
    this.decisionId = decisionId;
    return this;
  }

  @Override
  public MockDmnDecisionBuilder withVersionTag(final String versionTag) {
    this.versionTag = versionTag;
    return this;
  }

  @Override
  public void thenReturn(final Object decisionOutput) {
    if (decisionId == null || decisionId.trim().isEmpty()) {
      throw new IllegalStateException(
          "No decision ID set. Use withDecisionId(String) to set a decision ID before calling thenReturn().");
    }

    final String jsonOutput = client.getConfiguration().getJsonMapper().toJson(decisionOutput);

    final DmnModelInstance modelInstance = Dmn.createEmptyModel();

    final Definitions definitions = modelInstance.newInstance(Definitions.class);
    definitions.setName(decisionId + "-name");
    definitions.setNamespace("http://camunda.org/schema/1.0/dmn");
    modelInstance.setDefinitions(definitions);

    final Decision decision = modelInstance.newInstance(Decision.class);
    decision.setId(decisionId);
    decision.setName(decisionId + "-decision-name");
    definitions.addChildElement(decision);

    if (versionTag != null) {
      final ExtensionElements extensionElements =
          modelInstance.newInstance(ExtensionElements.class);
      extensionElements
          .addExtensionElement(ZEEBE_NAMESPACE, "versionTag")
          .setAttributeValue("value", versionTag);
      decision.setExtensionElements(extensionElements);
    }

    final LiteralExpression literalExpression = modelInstance.newInstance(LiteralExpression.class);
    final Text text = modelInstance.newInstance(Text.class);
    text.setTextContent(jsonOutput);
    literalExpression.setText(text);
    decision.addChildElement(literalExpression);

    LOGGER.debug(
        "Mock: Deploy a DMN [decisionId: '{}'] with version tag '{}' and decision output {}",
        decisionId,
        versionTag,
        decisionOutput);

    final String resourceName = decisionId + ".dmn";
    client
        .newDeployResourceCommand()
        .addResourceStream(
            new ByteArrayInputStream(Dmn.convertToString(modelInstance).getBytes()), resourceName)
        .send()
        .join();
  }
}
