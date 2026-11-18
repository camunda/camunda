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
package org.camunda.bpm.model.dmn.impl;

import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN11_ALTERNATIVE_NS;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN11_NS;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN12_NS;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN13_NS;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN14_NS;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN15_NS;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_11_ALTERNATIVE_SCHEMA_LOCATION;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_11_SCHEMA_LOCATION;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_12_SCHEMA_LOCATION;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_13_SCHEMA_LOCATION;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_14_SCHEMA_LOCATION;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_15_SCHEMA_LOCATION;

import java.io.InputStream;

import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelException;
import org.camunda.bpm.model.xml.ModelParseException;
import org.camunda.bpm.model.xml.impl.ModelImpl;
import org.camunda.bpm.model.xml.impl.parser.AbstractModelParser;
import org.camunda.bpm.model.xml.impl.util.ReflectUtil;
import org.camunda.bpm.model.xml.instance.DomDocument;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.SchemaFactory;

public class DmnParser extends AbstractModelParser {

  private static final String JAXP_SCHEMA_SOURCE = "http://java.sun.com/xml/jaxp/properties/schemaSource";
  private static final String JAXP_SCHEMA_LANGUAGE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";

  private static final String W3C_XML_SCHEMA = "http://www.w3.org/2001/XMLSchema";

  public DmnParser() {
    this.schemaFactory = SchemaFactory.newInstance(W3C_XML_SCHEMA);
    addSchema(DMN15_NS, createSchema(DMN_15_SCHEMA_LOCATION, DmnParser.class.getClassLoader()));
    addSchema(DMN14_NS, createSchema(DMN_14_SCHEMA_LOCATION, DmnParser.class.getClassLoader()));
    addSchema(DMN13_NS, createSchema(DMN_13_SCHEMA_LOCATION, DmnParser.class.getClassLoader()));
    addSchema(DMN12_NS, createSchema(DMN_12_SCHEMA_LOCATION, DmnParser.class.getClassLoader()));
    addSchema(DMN11_NS, createSchema(DMN_11_SCHEMA_LOCATION, DmnParser.class.getClassLoader()));
    addSchema(DMN11_ALTERNATIVE_NS, createSchema(DMN_11_ALTERNATIVE_SCHEMA_LOCATION, DmnParser.class.getClassLoader()));
  }

  @Override
  protected void configureFactory(DocumentBuilderFactory dbf) {
    dbf.setAttribute(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
    dbf.setAttribute(JAXP_SCHEMA_SOURCE, new String[] {
      ReflectUtil.getResource(DMN_15_SCHEMA_LOCATION, DmnParser.class.getClassLoader()).toString(),
      ReflectUtil.getResource(DMN_14_SCHEMA_LOCATION, DmnParser.class.getClassLoader()).toString(),
      ReflectUtil.getResource(DMN_13_SCHEMA_LOCATION, DmnParser.class.getClassLoader()).toString(),
      ReflectUtil.getResource(DMN_12_SCHEMA_LOCATION, DmnParser.class.getClassLoader()).toString(),
      ReflectUtil.getResource(DMN_11_SCHEMA_LOCATION, DmnParser.class.getClassLoader()).toString(),
      ReflectUtil.getResource(DMN_11_ALTERNATIVE_SCHEMA_LOCATION, DmnParser.class.getClassLoader()).toString()
    });
    super.configureFactory(dbf);
  }

  @Override
  protected DmnModelInstanceImpl createModelInstance(DomDocument document) {
    return new DmnModelInstanceImpl((ModelImpl) Dmn.INSTANCE.getDmnModel(), Dmn.INSTANCE.getDmnModelBuilder(), document);
  }

  @Override
  public DmnModelInstanceImpl parseModelFromStream(InputStream inputStream) {
    try {
      return (DmnModelInstanceImpl) super.parseModelFromStream(inputStream);
    }
    catch (ModelParseException e) {
      throw new DmnModelException("Unable to parse model", e);
    }
  }

  @Override
  public DmnModelInstanceImpl getEmptyModel() {
    return (DmnModelInstanceImpl) super.getEmptyModel();
  }
}
