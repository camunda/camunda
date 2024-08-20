/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import io.camunda.exporter.entities.operate.ProcessEntity;
import io.camunda.exporter.entities.operate.ProcessFlowNodeEntity;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.camunda.bpm.model.xml.ModelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class XMLUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(XMLUtils.class);

  private SAXParserFactory getSAXParserFactory() {
    final SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
    saxParserFactory.setNamespaceAware(true);
    try {
      saxParserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      saxParserFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      saxParserFactory.setFeature(
          "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      return saxParserFactory;
    } catch (final ParserConfigurationException | SAXException e) {
      LOGGER.error("Error creating SAXParser", e);
      throw new RuntimeException(e);
    }
  }

  public Optional<ProcessEntity> extractDiagramData(
      final byte[] byteArray, final String bpmnProcessId) {
    final SAXParserFactory saxParserFactory = getSAXParserFactory();
    InputStream is = new ByteArrayInputStream(byteArray);
    final BpmnXmlParserHandler handler = new BpmnXmlParserHandler();
    try {
      saxParserFactory.newSAXParser().parse(is, handler);
      final ProcessEntity processEntity = handler.getProcessEntity(bpmnProcessId);
      if (processEntity == null) {
        return Optional.empty();
      }
      final Set<String> processChildrenIds = handler.getProcessChildrenIds(bpmnProcessId);
      is = new ByteArrayInputStream(byteArray);
      final BpmnModelInstance modelInstance = Bpmn.readModelFromStream(is);
      final Collection<FlowNode> flowNodes = modelInstance.getModelElementsByType(FlowNode.class);
      flowNodes.stream()
          .filter(x -> processChildrenIds.contains(x.getId()))
          .toList()
          .forEach(
              x ->
                  processEntity
                      .getFlowNodes()
                      .add(new ProcessFlowNodeEntity(x.getId(), x.getName())));
      return Optional.of(processEntity);
    } catch (final ParserConfigurationException | SAXException | IOException | ModelException e) {
      LOGGER.warn("Unable to parse diagram: " + e.getMessage(), e);
      return Optional.empty();
    }
  }

  public static class BpmnXmlParserHandler extends DefaultHandler {

    private final String processElement = "process";
    private final List<ProcessEntity> processEntities = new ArrayList<>();
    private final Map<String, Set<String>> processChildrenIds = new LinkedHashMap<>();
    private String currentProcessId = null;

    @Override
    public void startElement(
        final String uri, final String localName, final String qName, final Attributes attributes)
        throws SAXException {
      final String elementId = attributes.getValue("id");
      if (localName.equalsIgnoreCase(processElement)) {
        if (elementId == null) {
          throw new SAXException("Process has null id");
        }
        processEntities.add(
            new ProcessEntity().setBpmnProcessId(elementId).setName(attributes.getValue("name")));
        processChildrenIds.put(elementId, new LinkedHashSet<>());
        currentProcessId = elementId;
      } else if (currentProcessId != null && elementId != null) {
        processChildrenIds.get(currentProcessId).add(elementId);
      }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName)
        throws SAXException {
      if (localName.equalsIgnoreCase(processElement)) {
        currentProcessId = null;
      }
    }

    public ProcessEntity getProcessEntity(final String processId) {
      return processEntities.stream()
          .filter(x -> Objects.equals(x.getBpmnProcessId(), processId))
          .findFirst()
          .orElse(null);
    }

    public Set<String> getProcessChildrenIds(final String processId) {
      return processChildrenIds.containsKey(processId)
          ? processChildrenIds.get(processId)
          : new HashSet<>();
    }
  }
}
