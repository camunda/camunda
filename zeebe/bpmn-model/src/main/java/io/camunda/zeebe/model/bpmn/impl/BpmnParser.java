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

package io.camunda.zeebe.model.bpmn.impl;

import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN20_NS;
import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_20_SCHEMA_LOCATION;

import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.InputStream;
import java.net.URL;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.camunda.bpm.model.xml.ModelValidationException;
import org.camunda.bpm.model.xml.impl.ModelImpl;
import org.camunda.bpm.model.xml.impl.parser.AbstractModelParser;
import org.camunda.bpm.model.xml.impl.util.ReflectUtil;
import org.camunda.bpm.model.xml.instance.DomDocument;
import org.xml.sax.SAXException;

/**
 * The parser used when parsing BPMN Files
 *
 * @author Daniel Meyer
 */
public class BpmnParser extends AbstractModelParser {

  public BpmnParser() {
    schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    addSchema(BPMN20_NS, BundledSchema.BPMN_20);
  }

  @Override
  protected void configureFactory(final DocumentBuilderFactory dbf) {
    super.configureFactory(dbf);
    // This only turns off DTD validation. Models are still validated against the schema set below.
    dbf.setValidating(false);
    dbf.setSchema(BundledSchema.BPMN_20);
  }

  @Override
  protected String resolveAccessExternalSchemaProperty() {
    return "";
  }

  @Override
  public BpmnModelInstanceImpl parseModelFromStream(final InputStream inputStream) {
    return (BpmnModelInstanceImpl) super.parseModelFromStream(inputStream);
  }

  @Override
  public BpmnModelInstanceImpl getEmptyModel() {
    return (BpmnModelInstanceImpl) super.getEmptyModel();
  }

  @Override
  protected BpmnModelInstanceImpl createModelInstance(final DomDocument document) {
    return new BpmnModelInstanceImpl(
        (ModelImpl) Bpmn.INSTANCE.getBpmnModel(), Bpmn.INSTANCE.getBpmnModelBuilder(), document);
  }

  private static final class BundledSchema {
    private static final Schema BPMN_20 = compile(BPMN_20_SCHEMA_LOCATION);

    private static Schema compile(final String location) {
      final URL url = ReflectUtil.getResource(location, BpmnParser.class.getClassLoader());
      try {
        return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(url);
      } catch (final SAXException e) {
        throw new ModelValidationException("Unable to compile schema " + url, e);
      }
    }
  }
}
