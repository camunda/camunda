/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.tasklist.util;

import io.camunda.migration.api.MigrationException;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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

  public XMLUtil() throws MigrationException {
    saxParserFactory = SAXParserFactory.newInstance();
    saxParserFactory.setNamespaceAware(true);
    try {
      saxParserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      saxParserFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      saxParserFactory.setFeature(
          "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    } catch (final Exception e) {
      throw new MigrationException("Error creating SAXParser", e);
    }
  }

  public Optional<ProcessEntity> extractDiagramData(
      final byte[] byteArray, final String bpmnProcessId) {
    final InputStream is = new ByteArrayInputStream(byteArray);
    final BpmnXmlParserHandler handler = new BpmnXmlParserHandler();
    try {
      saxParserFactory.newSAXParser().parse(is, handler);
      final Optional<ProcessEntity> processEntityOpt = handler.getProcessEntity(bpmnProcessId);
      if (processEntityOpt.isEmpty()) {
        return Optional.empty();
      }
      final ProcessEntity processEntity = processEntityOpt.get();
      processEntity.setVersionTag(handler.versionTag);
      processEntity.setIsPublic(handler.isPublic);
      processEntity.setFormId(handler.formId);
      return Optional.of(processEntity);
    } catch (final ParserConfigurationException | SAXException | IOException | ModelException e) {
      LOGGER.warn("Unable to parse diagram: " + e.getMessage(), e);
      return Optional.empty();
    }
  }

  public static class BpmnXmlParserHandler extends DefaultHandler {

    private final String processElement = "process";
    private final String startEventElement = "startEvent";
    private final String formDefinitionProperty = "formDefinition";
    private final String publicAccess = "publicAccess";
    private final List<ProcessEntity> processEntities = new ArrayList<>();
    private final Map<String, Set<String>> processChildrenIds = new LinkedHashMap<>();
    private String currentProcessId = null;
    private boolean isStartEvent = false;
    private String versionTag = null;
    private boolean isPublic = false;
    private String formId = null;

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
      } else if ("versionTag".equalsIgnoreCase(localName)) {
        versionTag = attributes.getValue("value");
      } else if (isStartEvent) {
        if (formDefinitionProperty.equalsIgnoreCase(localName)) {
          if (attributes.getValue("formKey") != null) {
            formId = attributes.getValue("formKey");
          }
        } else if ("property".equalsIgnoreCase(localName)) {
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
