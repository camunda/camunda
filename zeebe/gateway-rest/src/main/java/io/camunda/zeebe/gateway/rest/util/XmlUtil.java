/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.util;

import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.service.ProcessDefinitionServices;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class XmlUtil {

  private static final Logger LOG = LoggerFactory.getLogger(XmlUtil.class);
  private final ProcessDefinitionServices processDefinitionServices;

  public XmlUtil(final ProcessDefinitionServices processDefinitionServices) {
    this.processDefinitionServices = processDefinitionServices;
  }

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
      throw new RuntimeException("Error creating SAXParser: " + e.getMessage(), e);
    }
  }

  public void extractFlowNodeNames(
      final Long processDefinitionKey, final BiConsumer<Long, ProcessFlowNode> flowNodeConsumer) {
    final var processDefinition = processDefinitionServices.getByKey(processDefinitionKey);
    extractFlowNodeNames(processDefinition, flowNodeConsumer);
  }

  public void extractFlowNodeNames(
      final Set<Long> processDefinitionKeys,
      final BiConsumer<Long, ProcessFlowNode> flowNodeConsumer) {
    final var keysList = new ArrayList<>(processDefinitionKeys);
    final var result =
        processDefinitionServices.search(
            ProcessDefinitionQuery.of(q -> q.filter(f -> f.processDefinitionKeys(keysList))));

    for (final var processDefinition : result.items()) {
      extractFlowNodeNames(processDefinition, flowNodeConsumer);
    }
  }

  private void extractFlowNodeNames(
      final ProcessDefinitionEntity processDefinition,
      final BiConsumer<Long, ProcessFlowNode> flowNodeConsumer) {
    final var saxParserFactory = getSAXParserFactory();
    final var is =
        new ByteArrayInputStream(processDefinition.bpmnXml().getBytes(StandardCharsets.UTF_8));
    final var handler = new BpmnXmlParserHandler(processDefinition, flowNodeConsumer);
    try {
      saxParserFactory.newSAXParser().parse(is, handler);
    } catch (final ParserConfigurationException | SAXException | IOException e) {
      LOG.warn("Unable to parse diagram: " + e.getMessage(), e);
    }
  }

  public record ProcessFlowNode(String id, String name) {}

  public static class BpmnXmlParserHandler extends DefaultHandler {

    private final ProcessDefinitionEntity processDefinition;
    private final BiConsumer<Long, ProcessFlowNode> flowNodeConsumer;
    private boolean isCurrentProcess;

    public BpmnXmlParserHandler(
        final ProcessDefinitionEntity processDefinition,
        final BiConsumer<Long, ProcessFlowNode> flowNodeConsumer) {
      this.processDefinition = processDefinition;
      this.flowNodeConsumer = flowNodeConsumer;
    }

    @Override
    public void startElement(
        final String uri, final String localName, final String qName, final Attributes attributes) {
      final var id = attributes.getValue("id");
      if ("process".equalsIgnoreCase(localName)) {
        if (processDefinition.processDefinitionId().equals(id)) {
          isCurrentProcess = true;
        }
      } else if (isCurrentProcess) {
        final var name = attributes.getValue("name");
        if (name != null) {
          flowNodeConsumer.accept(
              processDefinition.processDefinitionKey(), new ProcessFlowNode(id, name));
        }
      }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) {
      if ("process".equalsIgnoreCase(localName)) {
        isCurrentProcess = false;
      }
    }
  }
}
