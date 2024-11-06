/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import io.camunda.webapps.schema.entities.operate.ProcessFlowNodeEntity;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.Query;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeFormDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperties;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperty;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import javax.xml.parsers.SAXParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  public Optional<ExtractableProcess> readProcessModel(
      final byte[] byteArray, final String bpmnProcessId) {
    try {
      final var is = new ByteArrayInputStream(byteArray);
      final var bpmnModelInstance = Bpmn.readModelFromStream(is);
      final var processModelInstance = bpmnModelInstance.getModelElementById(bpmnProcessId);
      if (processModelInstance instanceof final Process process) {
        return Optional.of(new ExtractableProcess(process));
      }
    } catch (final Throwable e) {
      LOGGER.warn("Unable to parse diagram: " + e.getMessage(), e);
    }
    return Optional.empty();
  }

  public static class ExtractableProcess {

    private final Process process;

    private ExtractableProcess(final Process process) {
      this.process = process;
    }

    public String getProcessName() {
      return process.getName();
    }

    public boolean hasPublicAccess() {
      final var startEvents = process.getChildElementsByType(StartEvent.class);
      if (startEvents != null) {
        return startEvents.stream().anyMatch(this::hasPublicAccessPropertyEnabled);
      }
      return false;
    }

    public Optional<StartFormLink> extractStartForm() {
      final var startEvents = process.getChildElementsByType(StartEvent.class);
      if (startEvents != null) {
        return startEvents.stream()
            .filter(this::hasFormDefinition)
            .findFirst()
            .map(this::getFormDefinition)
            .map(this::createStartFormLink);
      }
      return Optional.empty();
    }

    private boolean isProcessStartEvent(final StartEvent startEvent) {
      return process.equals(startEvent.getParentElement());
    }

    private boolean hasPublicAccessPropertyEnabled(final StartEvent startEvent) {
      return isProcessStartEvent(startEvent)
          && getExtensionProperties(startEvent).flatMap(this::hasPublicAccessProperty).isPresent();
    }

    private Optional<ZeebeProperty> hasPublicAccessProperty(final ZeebeProperties properties) {
      return properties.getProperties().stream()
          .filter(
              p ->
                  "publicAccess".equals(p.getName())
                      && Boolean.TRUE.toString().equals(p.getValue()))
          .findFirst();
    }

    private Optional<ZeebeProperties> getExtensionProperties(final StartEvent startEvent) {
      return getExtensionElements(startEvent)
          .map(ExtensionElements::getElementsQuery)
          .map(q -> q.filterByType(ZeebeProperties.class))
          .flatMap(Query::findSingleResult);
    }

    private boolean hasFormDefinition(final StartEvent startEvent) {
      return isProcessStartEvent(startEvent) && queryFormDefinition(startEvent).isPresent();
    }

    private ZeebeFormDefinition getFormDefinition(final StartEvent startEvent) {
      return queryFormDefinition(startEvent).orElse(null);
    }

    private StartFormLink createStartFormLink(final ZeebeFormDefinition formDefinition) {
      return new StartFormLink(formDefinition.getFormId());
    }

    private Optional<ZeebeFormDefinition> queryFormDefinition(final StartEvent startEvent) {
      return getExtensionElements(startEvent)
          .map(ExtensionElements::getElementsQuery)
          .map(q -> q.filterByType(ZeebeFormDefinition.class))
          .flatMap(Query::findSingleResult);
    }

    private Optional<ExtensionElements> getExtensionElements(final StartEvent startEvent) {
      return Optional.ofNullable(startEvent.getExtensionElements());
    }

    public Optional<List<ProcessFlowNodeEntity>> extractFlowNodes() {
      final var flowNodes = process.getChildElementsByType(FlowNode.class);
      if (flowNodes != null) {
        final var flowNodeEntities =
            flowNodes.stream().map(n -> new ProcessFlowNodeEntity(n.getId(), n.getName())).toList();
        return Optional.of(flowNodeEntities);
      }
      return Optional.empty();
    }
  }

  public record StartFormLink(String formId) {}
}
