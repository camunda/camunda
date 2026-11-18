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

import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.DecisionTable;
import org.camunda.bpm.model.dmn.instance.InputEntry;
import org.camunda.bpm.model.dmn.instance.Rule;
import org.junit.Test;

public class DmnWriterTest extends DmnModelTest {

  /**
   * <p>There is an issue in JDK 9+ that changes how CDATA section are serialized:
   *
   * <p>https://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8223291
   *
   * <p>&lt; JDK9:
   *
   * <pre>
   * &lt;inputEntry id="inputEntry1"&gt;
   *   &lt;text&gt;&lt;![CDATA[&gt;= 1000]]&gt;&lt;/text&gt;
   * &lt;/inputEntry&gt;
   * </pre>
   *
   * <p>(the text element has one child node, a CDATA section)
   *
   * <p>&gt;= JDK9:
   *
   * <pre>
   * &lt;inputEntry id="inputEntry1"&gt;
   *   &lt;text&gt;
   *     &lt;![CDATA[&gt;= 1000]]&gt;
   *   &lt;/text&gt;
   * &lt;/inputEntry&gt;
   * </pre>
   *
   * <p>(the text element has three child nodes, a text node, a CDATA section and another text node)
   *
   * <p>This test ensures, that a JDK9-formatted model can be read
   * and the text content method returns the CDATA value only
   */
  @Test
  public void shouldReadJDK9StyleModel()
  {
    DmnModelInstance modelInstance =
        Dmn.readModelFromStream(ExampleCompatibilityTest.class.getResourceAsStream("JDK9-style-CDATA.dmn"));

    Decision decision = (Decision) modelInstance.getDefinitions().getDrgElements().iterator().next();

    DecisionTable decisionTable = (DecisionTable) decision.getExpression();

    Rule rule = decisionTable.getRules().iterator().next();
    InputEntry inputEntry = rule.getInputEntries().iterator().next();
    String textContent = inputEntry.getText().getTextContent();
    assertThat(textContent).isEqualTo(">= 1000");
  }
}
