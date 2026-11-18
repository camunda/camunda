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
package org.camunda.bpm.model.dmn.util;

import org.w3c.dom.CDATASection;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xmlunit.util.Predicate;

/**
 * <p>In Java 8, a CDATA section is serialized with pretty printing as
 *
 * <pre>
 * {@code
 * <foo>
 *   <bar><![CDATA[data]]></bar>
 * </foo>
 * }
 * </pre>
 *
 * <p>whereas in Java 9+ it becomes
 *
 * <pre>
 * {@code
 * <foo>
 *   <bar>
 *      <![CDATA[data]]>
 *   </bar>
 * </foo>
 *}
 * </pre>
 *
 * <p>Note that the bar element in the second example has three children: a text with whitespace,
 * a cdata section and another text with whitespace. This is semantically different XML than the
 * first example.
 *
 * <p>This filter detects this whitespace pattern and removes the whitespace text
 * nodes before xmlunit performs the comparison.
 *
 * @see https://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8223291
 */
public class Java9CDataWhitespaceFilter implements Predicate<Node> {

  /**
   * @return false to remove the node from the comparison
   */
  @Override
  public boolean test(Node toTest) {
    return !isWhitespaceSurroundingCData(toTest);
  }

  private boolean isWhitespaceSurroundingCData(Node node) {

    if (isWhitespaceTextNode(node)) {
      Node parent = node.getParentNode();
      NodeList children = parent.getChildNodes();

      if (children.getLength() == 3) {
        Node child0 = children.item(0);
        Node child1 = children.item(1);
        Node child2 = children.item(2);

        return isTextNode(child0) && isCDataSection(child1) && isTextNode(child2);
      } else {
        return false;
      }

    } else {
      return false;
    }
  }

  private boolean isWhitespaceTextNode(Node node)
  {
    return isTextNode(node) && ((Text) node).getNodeValue().trim().isEmpty();
  }

  private boolean isTextNode(Node node) {
    return node instanceof Text;
  }

  private boolean isCDataSection(Node node) {
    return node instanceof CDATASection;
  }
}
