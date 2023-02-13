/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.util;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Optional;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.entities.ProcessFlowNodeEntity;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.xml.ModelException;
import org.camunda.bpm.model.xml.ModelParseException;
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

  private static final Logger logger = LoggerFactory.getLogger(XMLUtil.class);

  @Bean
  public SAXParserFactory getSAXParserFactory() {
    SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
    saxParserFactory.setNamespaceAware(true);
    try {
      saxParserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      saxParserFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      saxParserFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      return saxParserFactory;
    } catch (ParserConfigurationException | SAXException e) {
      logger.error("Error creating SAXParser", e);
      throw new RuntimeException(e);
    }
  }

  public Optional<ProcessEntity> extractDiagramData(byte[] byteArray) {
    SAXParserFactory saxParserFactory = getSAXParserFactory();
    InputStream is = new ByteArrayInputStream(byteArray);
    BpmnXmlParserHandler handler = new BpmnXmlParserHandler();
    try {
      saxParserFactory.newSAXParser().parse(is, handler);
      ProcessEntity processEntity = handler.getProcessEntity();
      is = new ByteArrayInputStream(byteArray);
      BpmnModelInstance modelInstance = Bpmn.readModelFromStream(is);
      Collection<FlowNode> flowNodes = modelInstance.getModelElementsByType(FlowNode.class);
      flowNodes.forEach(x -> processEntity.getFlowNodes().add(new ProcessFlowNodeEntity(x.getId(), x.getName())));
      return Optional.of(processEntity);
    } catch (ParserConfigurationException | SAXException | IOException | ModelException e) {
      logger.warn("Unable to parse diagram: " + e.getMessage(), e);
      return Optional.empty();
    }
  }

  public static class BpmnXmlParserHandler extends DefaultHandler {

    ProcessEntity processEntity = new ProcessEntity();

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
      if (localName.equalsIgnoreCase("process")) {
        if (attributes.getValue("name") != null) {
          processEntity.setName(attributes.getValue("name"));
        }
      }
    }

    public ProcessEntity getProcessEntity() {
      return processEntity;
    }
  }

}
