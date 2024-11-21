/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import io.camunda.webapps.schema.entities.operate.ProcessFlowNodeEntity;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.CallActivity;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.Process;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.camunda.bpm.model.xml.ModelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class XMLUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(XMLUtil.class);

  private final SAXParserFactory saxParserFactory;

  public XMLUtil() {
    saxParserFactory = SAXParserFactory.newInstance();
    saxParserFactory.setNamespaceAware(true);
    try {
      saxParserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      saxParserFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      saxParserFactory.setFeature(
          "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    } catch (final Exception e) {
      throw new ExporterException("Error creating SAXParser", e);
    }
  }

  public Optional<ProcessModelReader> createProcessModelReader(
      final byte[] byteArray, final String bpmnProcessId) {
    try {
      final var is = new ByteArrayInputStream(byteArray);
      final var bpmnModelInstance = Bpmn.readModelFromStream(is);
      final var processModelInstance = bpmnModelInstance.getModelElementById(bpmnProcessId);
      if (processModelInstance instanceof final Process process) {
        return Optional.of(new ProcessModelReader(process));
      }
    } catch (final Exception e) {
      LOGGER.warn("Unable to parse diagram: " + e.getMessage(), e);
    }
    return Optional.empty();
  }

  public Optional<ProcessEntity> extractDiagramData(
      final byte[] byteArray, final String bpmnProcessId) {
    InputStream is = new ByteArrayInputStream(byteArray);
    final BpmnXmlParserHandler handler = new BpmnXmlParserHandler();
    try {
      saxParserFactory.newSAXParser().parse(is, handler);
      final Optional<ProcessEntity> processEntityOpt = handler.getProcessEntity(bpmnProcessId);
      if (processEntityOpt.isEmpty()) {
        return Optional.empty();
      }
      final ProcessEntity processEntity = processEntityOpt.get();
      processEntity.setIsPublic(handler.isPublic);
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
      // collect call activity ids
      final Collection<CallActivity> callActivities =
          modelInstance.getModelElementsByType(CallActivity.class);
      processEntity.setCallActivityIds(
          callActivities.stream()
              .map(CallActivity::getId)
              .filter(id -> processChildrenIds.contains(id))
              .sorted()
              .toList());
      return Optional.of(processEntity);
    } catch (final ParserConfigurationException | SAXException | IOException | ModelException e) {
      LOGGER.warn("Unable to parse diagram: " + e.getMessage(), e);
      return Optional.empty();
    }
  }

  public static class BpmnXmlParserHandler extends DefaultHandler {

    private final String processElement = "process";
    private final String startEventElement = "startEvent";
    private final String publicAccess = "publicAccess";
    private final List<ProcessEntity> processEntities = new ArrayList<>();
    private final Map<String, Set<String>> processChildrenIds = new LinkedHashMap<>();
    private String currentProcessId = null;
    private boolean isStartEvent = false;
    private boolean isPublic = false;

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
      } else if (startEventElement.equalsIgnoreCase(localName)) {
        isStartEvent = true;
      } else if (currentProcessId != null && elementId != null) {
        processChildrenIds.get(currentProcessId).add(elementId);
      } else if (isStartEvent) {
        if ("property".equalsIgnoreCase(localName)) {
          final String name = attributes.getValue("name");
          final String value = attributes.getValue("value");
          if (publicAccess.equalsIgnoreCase(name)
              && Boolean.TRUE.toString().equalsIgnoreCase(value)) {
            isPublic = true;
          }
        }
      }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) {
      if (processElement.equalsIgnoreCase(localName)) {
        currentProcessId = null;
      } else if (startEventElement.equalsIgnoreCase(localName)) {
        isStartEvent = false;
      }
    }

    public Optional<ProcessEntity> getProcessEntity(final String processId) {
      return processEntities.stream()
          .filter(x -> Objects.equals(x.getBpmnProcessId(), processId))
          .findFirst();
    }

    public Set<String> getProcessChildrenIds(final String processId) {
      return processChildrenIds.containsKey(processId)
          ? processChildrenIds.get(processId)
          : new HashSet<>();
    }
  }
}
