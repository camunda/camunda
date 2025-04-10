/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.util;

import io.camunda.webapps.schema.entities.ProcessFlowNodeEntity;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

@Component
public class XMLUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(XMLUtil.class);

  private SAXParserFactory getSAXParserFactory() {
    final SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
    saxParserFactory.setNamespaceAware(true);
    try {
      saxParserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      saxParserFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      saxParserFactory.setFeature(
          "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      return saxParserFactory;
    } catch (ParserConfigurationException | SAXException e) {
      throw new RuntimeException("Error creating SAXParser: " + e.getMessage(), e);
    }
  }

  public void extractDiagramData(
      byte[] byteArray,
      Predicate<String> processIdPredicate,
      Consumer<String> nameConsumer,
      Consumer<ProcessFlowNodeEntity> flowNodeConsumer,
      BiConsumer<String, String> userTaskFormConsumer,
      Consumer<String> formKeyConsumer,
      Consumer<String> formIdConsumer,
      Consumer<Boolean> startedByFormConsumer) {
    final SAXParserFactory saxParserFactory = getSAXParserFactory();
    final InputStream is = new ByteArrayInputStream(byteArray);
    final BpmnXmlParserHandler handler =
        new BpmnXmlParserHandler(
            processIdPredicate,
            nameConsumer,
            flowNodeConsumer,
            userTaskFormConsumer,
            formKeyConsumer,
            formIdConsumer,
            startedByFormConsumer);
    try {
      saxParserFactory.newSAXParser().parse(is, handler);
    } catch (ParserConfigurationException | SAXException | IOException e) {
      LOGGER.warn("Unable to parse diagram: " + e.getMessage(), e);
    }
  }

  public static class BpmnXmlParserHandler extends DefaultHandler {

    private final Predicate<String> processIdPredicate;
    private final Consumer<String> nameConsumer;
    private final Consumer<ProcessFlowNodeEntity> flowNodeConsumer;
    private final BiConsumer<String, String> userTaskFormConsumer;
    private final Consumer<String> formKeyConsumer;
    private final Consumer<String> formIdConsumer;
    private final Consumer<Boolean> startedByFormConsumer;
    private boolean isUserTaskForm = false;

    private boolean isStartEvent = false;
    private boolean isCurrentProcess = false;

    private String userTaskFormId;
    private StringBuilder userTaskFormJson = new StringBuilder();

    public BpmnXmlParserHandler(
        final Predicate<String> processIdPredicate,
        final Consumer<String> nameConsumer,
        final Consumer<ProcessFlowNodeEntity> flowNodeConsumer,
        final BiConsumer<String, String> userTaskFormConsumer,
        final Consumer<String> formKeyConsumer,
        final Consumer<String> formIdConsumer,
        final Consumer<Boolean> startedByFormConsumer) {
      this.processIdPredicate = processIdPredicate;
      this.nameConsumer = nameConsumer;
      this.flowNodeConsumer = flowNodeConsumer;
      this.userTaskFormConsumer = userTaskFormConsumer;
      this.formKeyConsumer = formKeyConsumer;
      this.formIdConsumer = formIdConsumer;
      this.startedByFormConsumer = startedByFormConsumer;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
      if ("process".equalsIgnoreCase(localName)) {
        if (processIdPredicate.test(attributes.getValue("id"))) {
          isCurrentProcess = true;
          if (attributes.getValue("name") != null) {
            nameConsumer.accept(attributes.getValue("name"));
          }
        }
      } else if (!isCurrentProcess) {
        // element is not part of the current imported process, skip it
      } else if ("userTask".equalsIgnoreCase(localName)) {
        if (attributes.getValue("name") != null) {
          final ProcessFlowNodeEntity flowNodeEntity =
              new ProcessFlowNodeEntity(attributes.getValue("id"), attributes.getValue("name"));
          flowNodeConsumer.accept(flowNodeEntity);
        }
      } else if ("userTaskForm".equalsIgnoreCase(localName)) {
        isUserTaskForm = true;
        if (attributes.getValue("id") != null) {
          userTaskFormId = attributes.getValue("id");
        }
      } else if ("startEvent".equalsIgnoreCase(localName)) {
        isStartEvent = true;
      } else if (isStartEvent) {
        if ("formDefinition".equalsIgnoreCase(localName)) {
          if (attributes.getValue("formKey") != null) {
            formKeyConsumer.accept(attributes.getValue("formKey"));
          }
          if (attributes.getValue("formId") != null) {
            formIdConsumer.accept(attributes.getValue("formId"));
          }
        } else if ("property".equalsIgnoreCase(localName)) {
          final String name = attributes.getValue("name");
          final String value = attributes.getValue("value");
          if ("publicAccess".equalsIgnoreCase(name) && "true".equalsIgnoreCase(value)) {
            startedByFormConsumer.accept(true);
          }
        }
      }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) {
      if (!isCurrentProcess) {
        // element is not part of the current imported process, skip it
      } else if ("process".equalsIgnoreCase(localName)) {
        isCurrentProcess = false;
      } else if ("userTaskForm".equalsIgnoreCase(localName)) {
        userTaskFormConsumer.accept(userTaskFormId, userTaskFormJson.toString());
        isUserTaskForm = false;
        userTaskFormJson = new StringBuilder();
      } else if ("startEvent".equalsIgnoreCase(localName)) {
        isStartEvent = false;
      }
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) {
      if (isUserTaskForm) {
        userTaskFormJson.append(new String(ch, start, length));
      }
    }
  }
}
