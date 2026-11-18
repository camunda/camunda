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
package org.camunda.bpm.model.xml.impl.parser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.ModelValidationException;
import org.camunda.bpm.model.xml.impl.util.DomUtil;
import org.camunda.bpm.model.xml.impl.util.ReflectUtil;
import org.camunda.bpm.model.xml.instance.DomDocument;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.xml.sax.SAXException;

/**
 * @author Daniel Meyer
 *
 */
public abstract class AbstractModelParser {

  protected static final String JAXP_ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema";
  protected static final String JAXP_ACCESS_EXTERNAL_SCHEMA_SYSTEM_PROPERTY = "javax.xml.accessExternalSchema";
  protected static final String JAXP_ACCESS_EXTERNAL_SCHEMA_ALL = "all";

  private final DocumentBuilderFactory documentBuilderFactory;
  protected SchemaFactory schemaFactory;
  protected Map<String, Schema> schemas = new HashMap<>();

  protected AbstractModelParser() {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    configureFactory(dbf);
    this.documentBuilderFactory = dbf;
  }

  /**
   * allows subclasses to configure the {@link DocumentBuilderFactory}.
   * @param dbf the factory to configure
   */
  protected void configureFactory(DocumentBuilderFactory dbf) {
    dbf.setValidating(true);
    dbf.setIgnoringComments(false);
    dbf.setIgnoringElementContentWhitespace(false);
    dbf.setNamespaceAware(true);
    protectAgainstXxeAttacks(dbf);
    enableSecureProcessing(dbf);
  }

  /**
   * Configures the DocumentBuilderFactory in a way, that it is protected against XML External Entity Attacks.
   * If the implementing parser does not support one or multiple features, the failed feature is ignored.
   * The parser might not protected, if the feature assignment fails.
   *
   * @see <a href="https://www.owasp.org/index.php/XML_External_Entity_(XXE)_Prevention_Cheat_Sheet">OWASP Information of XXE attacks</a>
   *
   * @param dbf The factory to configure.
   */
  private void protectAgainstXxeAttacks(final DocumentBuilderFactory dbf) {
    try {
      dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
    } catch (ParserConfigurationException ignored) {
    }

    try {
      dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    } catch (ParserConfigurationException ignored) {
    }

    try {
      dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    } catch (ParserConfigurationException ignored) {
    }

    dbf.setXIncludeAware(false);
    dbf.setExpandEntityReferences(false);
  }

  private void enableSecureProcessing(final DocumentBuilderFactory dbf) {
    try {
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      dbf.setAttribute(JAXP_ACCESS_EXTERNAL_SCHEMA, resolveAccessExternalSchemaProperty());
    } catch (ParserConfigurationException | IllegalArgumentException ignored) {
      // ignored
    }
  }

  /*
   * JAXP allows users to override the default value via system properties and
   * a central properties file (see https://docs.oracle.com/javase/tutorial/jaxp/properties/scope.html).
   * However, both are overridden by an explicit configuration in code, as we apply it.
   * Since we want users to customize the value, we take the system property into account.
   * The properties file is not supported at the moment.
   */
  protected String resolveAccessExternalSchemaProperty() {
    String systemProperty = System.getProperty(JAXP_ACCESS_EXTERNAL_SCHEMA_SYSTEM_PROPERTY);

    if (systemProperty != null) {
      return systemProperty;
    } else {
      return JAXP_ACCESS_EXTERNAL_SCHEMA_ALL;
    }
  }

  public ModelInstance parseModelFromStream(InputStream inputStream) {
    DomDocument document = null;

    synchronized(documentBuilderFactory) {
      document = DomUtil.parseInputStream(documentBuilderFactory, inputStream);
    }

    validateModel(document);
    return createModelInstance(document);

  }

  public ModelInstance getEmptyModel() {
    DomDocument document = null;

    synchronized(documentBuilderFactory) {
      document = DomUtil.getEmptyDocument(documentBuilderFactory);
    }

    return createModelInstance(document);
  }

  /**
   * Validate DOM document
   *
   * @param document the DOM document to validate
   */
  public void validateModel(DomDocument document) {

    Schema schema = getSchema(document);

    if (schema == null) {
      return;
    }

    Validator validator = schema.newValidator();
    try {
      synchronized(document) {
        validator.validate(document.getDomSource());
      }
    } catch (IOException e) {
      throw new ModelValidationException("Error during DOM document validation", e);
    } catch (SAXException e) {
      throw new ModelValidationException("DOM document is not valid", e);
    }
  }

  protected Schema getSchema(DomDocument document) {
    DomElement rootElement = document.getRootElement();
    String namespaceURI = rootElement.getNamespaceURI();
    return schemas.get(namespaceURI);
  }

  protected void addSchema(String namespaceURI, Schema schema) {
    schemas.put(namespaceURI, schema);
  }

  protected Schema createSchema(String location, ClassLoader classLoader) {
    URL cmmnSchema = ReflectUtil.getResource(location, classLoader);
    try {
      return schemaFactory.newSchema(cmmnSchema);
    } catch (SAXException e) {
      throw new ModelValidationException("Unable to parse schema:" + cmmnSchema);
    }
  }

  protected abstract ModelInstance createModelInstance(DomDocument document);

}
