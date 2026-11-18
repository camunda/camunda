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
package org.camunda.bpm.model.dmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.File;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.camunda.bpm.model.dmn.instance.DmnElement;
import org.camunda.bpm.model.dmn.instance.DmnModelElementInstance;
import org.camunda.bpm.model.dmn.instance.NamedElement;
import org.camunda.bpm.model.dmn.util.Java9CDataWhitespaceFilter;
import org.camunda.bpm.model.dmn.util.ParseDmnModelRule;
import org.camunda.bpm.model.xml.impl.util.ReflectUtil;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Document;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

public abstract class DmnModelTest {

  public final static String TEST_NAMESPACE = "http://camunda.org/schema/1.0/dmn";

  @Rule
  public final ParseDmnModelRule parseDmnModelRule = new ParseDmnModelRule();

  @Rule
  public TemporaryFolder tmpFolder = new TemporaryFolder();

  protected DmnModelInstance modelInstance;

  @Before
  public void setup() {
    modelInstance = parseDmnModelRule.getDmnModel();
  }

  public <E extends NamedElement> E generateNamedElement(Class<E> elementClass) {
    return generateNamedElement(elementClass, "");
  }

  public <E extends NamedElement> E generateNamedElement(Class<E> elementClass, Integer suffix) {
    return generateNamedElement(elementClass, "", suffix);
  }

  public <E extends NamedElement> E generateNamedElement(Class<E> elementClass, String name) {
    return generateNamedElement(elementClass, name, null);
  }

  public <E extends NamedElement> E generateNamedElement(Class<E> elementClass, String name, Integer suffix) {
    E element = generateElement(elementClass, suffix);
    element.setName(name);
    return element;
  }

  public <E extends DmnModelElementInstance> E generateElement(Class<E> elementClass) {
    return generateElement(elementClass, null);
  }

  public <E extends DmnModelElementInstance> E generateElement(Class<E> elementClass, Integer suffix) {
    E element = modelInstance.newInstance(elementClass);
    if (element instanceof DmnElement) {
      String identifier = elementClass.getSimpleName();
      if (suffix != null) {
        identifier += suffix.toString();
      }
      identifier = Character.toLowerCase(identifier.charAt(0)) + identifier.substring(1);
      ((DmnElement) element).setId(identifier);
    }
    return element;
  }

  protected void assertModelEqualsFile(String expectedPath) throws Exception {
    File actualFile = tmpFolder.newFile();
    Dmn.writeModelToFile(actualFile, modelInstance);

    File expectedFile = ReflectUtil.getResourceAsFile(expectedPath);

    DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
    Document actualDocument = docBuilder.parse(actualFile);
    Document expectedDocument = docBuilder.parse(expectedFile);

    Diff diff = DiffBuilder.compare(expectedDocument).withTest(actualDocument)
      .withNodeFilter(new Java9CDataWhitespaceFilter())
      .checkForSimilar()
      .build();

    if (diff.hasDifferences()) {

      String failMsg = "XML differs:\n" + diff.getDifferences() +
          "\n\nActual XML:\n" + Dmn.convertToString(modelInstance);
      fail(failMsg);
    }
  }

  protected void assertElementIsEqualToId(DmnModelElementInstance actualElement, String id) {
    assertThat(actualElement).isNotNull();

    ModelElementInstance expectedElement = modelInstance.getModelElementById(id);
    assertThat(expectedElement).isNotNull();

    assertThat(actualElement).isEqualTo(expectedElement);
  }

}
