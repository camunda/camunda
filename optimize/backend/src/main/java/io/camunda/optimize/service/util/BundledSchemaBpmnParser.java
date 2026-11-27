/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import java.io.InputStream;
import java.util.Arrays;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.impl.BpmnParser;
import org.xml.sax.SAXException;

/**
 * Parses BPMN models and validates them only against the BPMN schema bundled with the model API.
 * Unlike {@link org.camunda.bpm.model.bpmn.Bpmn#readModelFromStream(InputStream)}, it ignores the
 * {@code xsi:schemaLocation} and {@code xsi:noNamespaceSchemaLocation} hints in the model, so a
 * model parses the same way whatever hints it carries.
 */
public final class BundledSchemaBpmnParser extends BpmnParser {

  private static final String JAXP_SCHEMA_LANGUAGE =
      "http://java.sun.com/xml/jaxp/properties/schemaLanguage";
  private static final String JAXP_SCHEMA_SOURCE =
      "http://java.sun.com/xml/jaxp/properties/schemaSource";

  private static final BundledSchemaBpmnParser INSTANCE = new BundledSchemaBpmnParser();

  private BundledSchemaBpmnParser() {}

  public static BpmnModelInstance readModelFromStream(final InputStream stream) {
    return INSTANCE.parseModelFromStream(stream);
  }

  @Override
  protected void configureFactory(final DocumentBuilderFactory dbf) {
    super.configureFactory(dbf);
    final Schema bundledSchema = compile((String) dbf.getAttribute(JAXP_SCHEMA_SOURCE));
    dbf.setAttribute(JAXP_SCHEMA_LANGUAGE, null);
    dbf.setAttribute(JAXP_SCHEMA_SOURCE, null);
    // only turns off DTD validation, which models don't use; the schema below still validates
    dbf.setValidating(false);
    dbf.setSchema(bundledSchema);
  }

  @Override
  protected String resolveAccessExternalSchemaProperty() {
    return "";
  }

  private static Schema compile(final String... schemaLocations) {
    final Source[] sources =
        Arrays.stream(schemaLocations).map(StreamSource::new).toArray(Source[]::new);
    try {
      return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(sources);
    } catch (final SAXException e) {
      throw new IllegalStateException("Unable to compile the bundled BPMN schema", e);
    }
  }
}
