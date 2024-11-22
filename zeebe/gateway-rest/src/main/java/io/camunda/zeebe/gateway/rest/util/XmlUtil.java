/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.util;

import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

@Component
public class XmlUtil {

  public static final String XPATH_NODE_NAME = "//process[@id='%s']/%s[@id='%s']/@name";
  private static final Logger LOG = LoggerFactory.getLogger(XmlUtil.class);

  private final ProcessDefinitionServices processDefinitionServices;

  public XmlUtil(final ProcessDefinitionServices processDefinitionServices) {
    this.processDefinitionServices = processDefinitionServices;
  }

  public String getFlowNodeName(final FlowNodeInstanceEntity flowNode) {
    final var processDefinition =
        processDefinitionServices.getByKey(flowNode.processDefinitionKey());
    final var type = BpmnElementType.valueOf(flowNode.type().name());
    if (type.getElementTypeName().isEmpty()) {
      return flowNode.flowNodeId();
    }
    return extractFlowNodeName(
        processDefinition.bpmnXml(),
        processDefinition.processDefinitionId(),
        type.getElementTypeName().get(),
        flowNode.flowNodeId());
  }

  public String getUserTaskName(final UserTaskEntity userTask) {
    final var processDefinition =
        processDefinitionServices.getByKey(userTask.processDefinitionKey());
    return extractFlowNodeName(
        processDefinition.bpmnXml(),
        processDefinition.processDefinitionId(),
        "userTask",
        userTask.elementId());
  }

  public String extractFlowNodeName(
      final String xml,
      final String processDefinitionId,
      final String flowNodeType,
      final String flowNodeId) {
    try {
      final var documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      final var xmlStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
      final var document = documentBuilder.parse(xmlStream);
      final var expression =
          XPATH_NODE_NAME.formatted(processDefinitionId, flowNodeType, flowNodeId);
      final var xPath = XPathFactory.newInstance().newXPath();
      final var name = (String) xPath.compile(expression).evaluate(document, XPathConstants.STRING);
      LOG.debug("XPath expression {}, result {}", expression, name);
      return StringUtils.isNotBlank(name) ? name : flowNodeId;
    } catch (final SAXException
        | IOException
        | XPathExpressionException
        | ParserConfigurationException e) {
      LOG.error(e.getMessage(), e);
    }
    return flowNodeId;
  }
}
