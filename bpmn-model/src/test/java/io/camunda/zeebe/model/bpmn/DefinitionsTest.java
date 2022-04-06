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

import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.XML_SCHEMA_NS;
import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.XPATH_NS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.model.bpmn.instance.Definitions;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.Import;
import io.camunda.zeebe.model.bpmn.instance.Message;
import io.camunda.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.Property;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.util.BpmnModelResource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.camunda.bpm.model.xml.ModelParseException;
import org.camunda.bpm.model.xml.ModelReferenceException;
import org.camunda.bpm.model.xml.ModelValidationException;
import org.camunda.bpm.model.xml.impl.util.IoUtil;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Daniel Meyer
 */
public class DefinitionsTest extends BpmnModelTest {

  @Test
  @BpmnModelResource
  public void shouldImportEmptyDefinitions() {

    final Definitions definitions = bpmnModelInstance.getDefinitions();
    assertThat(definitions).isNotNull();

    // provided in file
    assertThat(definitions.getTargetNamespace()).isEqualTo("http://camunda.org/test");

    // defaults provided in Schema
    assertThat(definitions.getExpressionLanguage()).isEqualTo(XPATH_NS);
    assertThat(definitions.getTypeLanguage()).isEqualTo(XML_SCHEMA_NS);

    // not provided in file
    assertThat(definitions.getExporter()).isNull();
    assertThat(definitions.getExporterVersion()).isNull();
    assertThat(definitions.getId()).isNull();
    assertThat(definitions.getName()).isNull();

    // has no imports
    assertThat(definitions.getImports()).isEmpty();
  }

  @Test
  public void shouldNotImportWrongOrderedSequence() {
    try {
      Bpmn.readModelFromStream(
          getClass()
              .getResourceAsStream("DefinitionsTest.shouldNotImportWrongOrderedSequence.bpmn"));
      Assert.fail("Model is invalid and should not pass the validation");
    } catch (final Exception e) {
      assertThat(e).isInstanceOf(ModelParseException.class);
    }
  }

  @Test
  public void shouldAddChildElementsInCorrectOrder() {
    // create an empty model
    final BpmnModelInstance bpmnModelInstance = Bpmn.createEmptyModel();

    // add definitions
    final Definitions definitions = bpmnModelInstance.newInstance(Definitions.class);
    definitions.setTargetNamespace("Examples");
    bpmnModelInstance.setDefinitions(definitions);

    // create a Process element and add it to the definitions
    Process process = bpmnModelInstance.newInstance(Process.class);
    process.setId("some-process-id");
    definitions.getRootElements().add(process);

    // create an Import element and add it to the definitions
    Import importElement = bpmnModelInstance.newInstance(Import.class);
    importElement.setNamespace("Imports");
    importElement.setLocation("here");
    importElement.setImportType("example");
    definitions.getImports().add(importElement);

    // create another Process element and add it to the definitions
    process = bpmnModelInstance.newInstance(Process.class);
    process.setId("another-process-id");
    definitions.getRootElements().add(process);

    // create another Import element and add it to the definitions
    importElement = bpmnModelInstance.newInstance(Import.class);
    importElement.setNamespace("Imports");
    importElement.setLocation("there");
    importElement.setImportType("example");
    definitions.getImports().add(importElement);

    // validate model
    try {
      Bpmn.validateModel(bpmnModelInstance);
    } catch (final ModelValidationException e) {
      Assert.fail();
    }
  }

  @Test
  @BpmnModelResource
  public void shouldNotAffectComments() throws IOException {
    final Definitions definitions = bpmnModelInstance.getDefinitions();
    assertThat(definitions).isNotNull();

    // create another Process element and add it to the definitions
    final Process process = bpmnModelInstance.newInstance(Process.class);
    process.setId("another-process-id");
    definitions.getRootElements().add(process);

    // create another Import element and add it to the definitions
    final Import importElement = bpmnModelInstance.newInstance(Import.class);
    importElement.setNamespace("Imports");
    importElement.setLocation("there");
    importElement.setImportType("example");
    definitions.getImports().add(importElement);

    // validate model
    try {
      Bpmn.validateModel(bpmnModelInstance);
    } catch (final ModelValidationException e) {
      Assert.fail();
    }

    // convert the model to the XML string representation
    final OutputStream outputStream = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(outputStream, bpmnModelInstance);
    InputStream inputStream = IoUtil.convertOutputStreamToInputStream(outputStream);
    final String modelString = IoUtil.getStringFromInputStream(inputStream);
    IoUtil.closeSilently(outputStream);
    IoUtil.closeSilently(inputStream);

    // read test process from file as string
    inputStream =
        getClass().getResourceAsStream("DefinitionsTest.shouldNotAffectCommentsResult.bpmn");
    final String fileString = IoUtil.getStringFromInputStream(inputStream);
    IoUtil.closeSilently(inputStream);

    // compare strings
    assertThat(modelString).endsWith(fileString);
  }

  @Test
  public void shouldAddMessageAndMessageEventDefinition() {
    // create empty model
    final BpmnModelInstance bpmnModelInstance = Bpmn.createEmptyModel();

    // add definitions to model
    final Definitions definitions = bpmnModelInstance.newInstance(Definitions.class);
    definitions.setTargetNamespace("Examples");
    bpmnModelInstance.setDefinitions(definitions);

    // create and add message
    final Message message = bpmnModelInstance.newInstance(Message.class);
    message.setId("start-message-id");
    definitions.getRootElements().add(message);

    // create and add message event definition
    final MessageEventDefinition messageEventDefinition =
        bpmnModelInstance.newInstance(MessageEventDefinition.class);
    messageEventDefinition.setId("message-event-def-id");
    messageEventDefinition.setMessage(message);
    definitions.getRootElements().add(messageEventDefinition);

    // test if message was set correctly
    final Message setMessage = messageEventDefinition.getMessage();
    assertThat(setMessage).isEqualTo(message);

    // add process
    final Process process = bpmnModelInstance.newInstance(Process.class);
    process.setId("messageEventDefinition");
    definitions.getRootElements().add(process);

    // add start event
    final StartEvent startEvent = bpmnModelInstance.newInstance(StartEvent.class);
    startEvent.setId("theStart");
    process.getFlowElements().add(startEvent);

    // create and add message event definition to start event
    final MessageEventDefinition startEventMessageEventDefinition =
        bpmnModelInstance.newInstance(MessageEventDefinition.class);
    startEventMessageEventDefinition.setMessage(message);
    startEvent.getEventDefinitions().add(startEventMessageEventDefinition);

    // create another message but do not add it
    final Message anotherMessage = bpmnModelInstance.newInstance(Message.class);
    anotherMessage.setId("another-message-id");

    // create a message event definition and try to add last create message
    MessageEventDefinition anotherMessageEventDefinition =
        bpmnModelInstance.newInstance(MessageEventDefinition.class);
    try {
      anotherMessageEventDefinition.setMessage(anotherMessage);
      Assert.fail(
          "Message should not be added to message event definition, cause it is not part of the model");
    } catch (final Exception e) {
      assertThat(e).isInstanceOf(ModelReferenceException.class);
    }

    // first add message to model than to event definition
    definitions.getRootElements().add(anotherMessage);
    anotherMessageEventDefinition.setMessage(anotherMessage);
    startEvent.getEventDefinitions().add(anotherMessageEventDefinition);

    // message event definition and add message by id to it
    anotherMessageEventDefinition = bpmnModelInstance.newInstance(MessageEventDefinition.class);
    startEvent.getEventDefinitions().add(anotherMessageEventDefinition);

    // validate model
    try {
      Bpmn.validateModel(bpmnModelInstance);
    } catch (final ModelValidationException e) {
      Assert.fail();
    }
  }

  @Test
  public void shouldAddParentChildElementInCorrectOrder() {
    // create empty model
    final BpmnModelInstance bpmnModelInstance = Bpmn.createEmptyModel();

    // add definitions to model
    final Definitions definitions = bpmnModelInstance.newInstance(Definitions.class);
    definitions.setTargetNamespace("Examples");
    bpmnModelInstance.setDefinitions(definitions);

    // add process
    final Process process = bpmnModelInstance.newInstance(Process.class);
    process.setId("messageEventDefinition");
    definitions.getRootElements().add(process);

    // add start event
    final StartEvent startEvent = bpmnModelInstance.newInstance(StartEvent.class);
    startEvent.setId("theStart");
    process.getFlowElements().add(startEvent);

    // create and add message
    final Message message = bpmnModelInstance.newInstance(Message.class);
    message.setId("start-message-id");
    definitions.getRootElements().add(message);

    // add message event definition to start event
    final MessageEventDefinition startEventMessageEventDefinition =
        bpmnModelInstance.newInstance(MessageEventDefinition.class);
    startEventMessageEventDefinition.setMessage(message);
    startEvent.getEventDefinitions().add(startEventMessageEventDefinition);

    // add property after message event definition
    final Property property = bpmnModelInstance.newInstance(Property.class);
    startEvent.getProperties().add(property);

    // finally add an extensions element
    final ExtensionElements extensionElements =
        bpmnModelInstance.newInstance(ExtensionElements.class);
    process.setExtensionElements(extensionElements);

    // validate model
    try {
      Bpmn.validateModel(bpmnModelInstance);
    } catch (final ModelValidationException e) {
      Assert.fail();
    }
  }
}
