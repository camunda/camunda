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
package org.camunda.bpm.model.xml.impl.instance;

import org.camunda.bpm.model.xml.ModelException;
import org.camunda.bpm.model.xml.impl.util.DomUtil;
import org.camunda.bpm.model.xml.impl.util.XmlQName;
import org.camunda.bpm.model.xml.instance.DomDocument;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.transform.dom.DOMSource;
import java.util.List;

import static javax.xml.XMLConstants.XMLNS_ATTRIBUTE_NS_URI;

/**
 * @author Sebastian Menski
 */
public class DomDocumentImpl implements DomDocument {

  public static final String GENERIC_NS_PREFIX = "ns";

  private final Document document;

  public DomDocumentImpl(Document document) {
    this.document = document;
  }

  public DomElement getRootElement() {
    synchronized(document) {
      Element documentElement = document.getDocumentElement();
      if (documentElement != null) {
        return new DomElementImpl(documentElement);
      }
      else {
        return null;
      }
    }

  }

  public void setRootElement(DomElement rootElement) {
    synchronized(document) {
      Element documentElement = document.getDocumentElement();
      Element newDocumentElement = ((DomElementImpl) rootElement).getElement();
      if (documentElement != null) {
        document.replaceChild(newDocumentElement, documentElement);
      }
      else {
        document.appendChild(newDocumentElement);
      }
    }
  }

  public DomElement createElement(String namespaceUri, String localName) {
    synchronized(document) {
      XmlQName xmlQName = new XmlQName(this, namespaceUri, localName);
      Element element = document.createElementNS(xmlQName.getNamespaceUri(), xmlQName.getPrefixedName());
      return new DomElementImpl(element);
    }
  }

  public DomElement getElementById(String id) {
    synchronized(document) {
      Element element = document.getElementById(id);
      if (element != null) {
        return new DomElementImpl(element);
      }
      else {
        return null;
      }
    }
  }

  public List<DomElement> getElementsByNameNs(String namespaceUri, String localName) {
    synchronized(document) {
      NodeList elementsByTagNameNS = document.getElementsByTagNameNS(namespaceUri, localName);
      return DomUtil.filterNodeListByName(elementsByTagNameNS, namespaceUri, localName);
    }
  }

  public DOMSource getDomSource() {
    return new DOMSource(document);
  }

  public String registerNamespace(String namespaceUri) {
    synchronized(document) {
      DomElement rootElement = getRootElement();
      if (rootElement != null) {
        return rootElement.registerNamespace(namespaceUri);
      }
      else {
        throw new ModelException("Unable to define a new namespace without a root document element");
      }
    }
  }

  public void registerNamespace(String prefix, String namespaceUri) {
    synchronized(document) {
      DomElement rootElement = getRootElement();
      if (rootElement != null) {
        rootElement.registerNamespace(prefix, namespaceUri);
      }
      else {
        throw new ModelException("Unable to define a new namespace without a root document element");
      }
    }
  }

  protected String getUnusedGenericNsPrefix() {
    synchronized(document) {
      Element documentElement = document.getDocumentElement();
      if (documentElement == null) {
        return GENERIC_NS_PREFIX + "0";
      }
      else {
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
          if (!documentElement.hasAttributeNS(XMLNS_ATTRIBUTE_NS_URI, GENERIC_NS_PREFIX + i)) {
            return GENERIC_NS_PREFIX + i;
          }
        }
        throw new ModelException("Unable to find an unused namespace prefix");
      }
    }
  }

  public DomDocument clone() {
    synchronized(document) {
      return new DomDocumentImpl((Document) document.cloneNode(true));
    }
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    DomDocumentImpl that = (DomDocumentImpl) o;
    return document.equals(that.document);
  }

  public int hashCode() {
    return document.hashCode();
  }
}
