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

package io.camunda.zeebe.model.bpmn.instance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.TransactionMethod;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.camunda.bpm.model.xml.impl.util.ReflectUtil;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * @author Thorben Lindhauer
 */
public class TransactionTest extends BpmnModelElementInstanceTest {

  @Override
  public TypeAssumption getTypeAssumption() {
    return new TypeAssumption(SubProcess.class, false);
  }

  @Override
  public Collection<ChildElementAssumption> getChildElementAssumptions() {
    return Collections.emptyList();
  }

  @Override
  public Collection<AttributeAssumption> getAttributesAssumptions() {
    return Arrays.asList(
        new AttributeAssumption("method", false, false, TransactionMethod.Compensate));
  }

  @Test
  public void shouldReadTransaction() {
    final InputStream inputStream =
        ReflectUtil.getResourceAsStream("io/camunda/zeebe/model/bpmn/TransactionTest.xml");
    final Transaction transaction =
        Bpmn.readModelFromStream(inputStream).getModelElementById("transaction");

    assertThat(transaction).isNotNull();
    assertThat(transaction.getMethod()).isEqualTo(TransactionMethod.Image);
    assertThat(transaction.getFlowElements()).hasSize(1);
  }

  @Test
  public void shouldWriteTransaction()
      throws ParserConfigurationException, SAXException, IOException {
    // given a model
    final BpmnModelInstance newModel = Bpmn.createProcess("process").done();

    final Process process = newModel.getModelElementById("process");

    final Transaction transaction = newModel.newInstance(Transaction.class);
    transaction.setId("transaction");
    transaction.setMethod(TransactionMethod.Store);
    process.addChildElement(transaction);

    // that is written to a stream
    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(outStream, newModel);

    // when reading from that stream
    final ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());

    final DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
    final DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
    final Document actualDocument = docBuilder.parse(inStream);

    // then it possible to traverse to the transaction element and assert its attributes
    final NodeList transactionElements = actualDocument.getElementsByTagName("transaction");
    assertThat(transactionElements.getLength()).isEqualTo(1);

    final Node transactionElement = transactionElements.item(0);
    assertThat(transactionElement).isNotNull();
    final Node methodAttribute = transactionElement.getAttributes().getNamedItem("method");
    assertThat(methodAttribute.getNodeValue()).isEqualTo("##Store");
  }
}
