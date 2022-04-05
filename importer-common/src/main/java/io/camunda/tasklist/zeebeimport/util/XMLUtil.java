/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.util;

import io.camunda.tasklist.entities.ProcessFlowNodeEntity;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

@Component
@Configuration
public class XMLUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(XMLUtil.class);

  @Bean
  public SAXParserFactory getSAXParserFactory() {
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
      Consumer<String> nameConsumer,
      Consumer<ProcessFlowNodeEntity> flowNodeConsumer,
      BiConsumer<String, String> userTaskFormConsumer) {
    final SAXParserFactory saxParserFactory = getSAXParserFactory();
    final InputStream is = new ByteArrayInputStream(byteArray);
    final BpmnXmlParserHandler handler =
        new BpmnXmlParserHandler(nameConsumer, flowNodeConsumer, userTaskFormConsumer);
    try {
      saxParserFactory.newSAXParser().parse(is, handler);
    } catch (ParserConfigurationException | SAXException | IOException e) {
      LOGGER.warn("Unable to parse diagram: " + e.getMessage(), e);
    }
  }

  public static class BpmnXmlParserHandler extends DefaultHandler {

    private Consumer<String> nameConsumer;
    private Consumer<ProcessFlowNodeEntity> flowNodeConsumer;
    private BiConsumer<String, String> userTaskFormConsumer;

    private boolean isUserTaskForm = false;

    private String userTaskFormId;
    private StringBuilder userTaskFormJson = new StringBuilder();

    public BpmnXmlParserHandler(
        final Consumer<String> nameConsumer,
        final Consumer<ProcessFlowNodeEntity> flowNodeConsumer,
        final BiConsumer<String, String> userTaskFormConsumer) {
      this.nameConsumer = nameConsumer;
      this.flowNodeConsumer = flowNodeConsumer;
      this.userTaskFormConsumer = userTaskFormConsumer;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
      if ("process".equalsIgnoreCase(localName)) {
        if (attributes.getValue("name") != null) {
          nameConsumer.accept(attributes.getValue("name"));
        }
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
      }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) {
      if ("userTaskForm".equalsIgnoreCase(localName)) {
        userTaskFormConsumer.accept(userTaskFormId, userTaskFormJson.toString());
        isUserTaskForm = false;
        userTaskFormJson = new StringBuilder();
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
