/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import static io.camunda.zeebe.protocol.record.value.BpmnElementType.SUB_PROCESS;

import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Parses a BPMN 2.0 classpath resource into an immutable {@link ProcessGraph}.
 *
 * <p>This class is responsible only for the build phase: reading the resource file, parsing the XML
 * DOM, and extracting element types, sequence-flow successors, and nested sub-process scopes.
 * Walking the graph to produce per-instance execution paths is the responsibility of {@link
 * ProcessGraph#walk}.
 *
 * <p>Callers should invoke {@link #parse} once per process ID (before any instance loop) and reuse
 * the returned {@link ProcessGraph} for all instances of that process.
 */
final class BpmnFlowParser {

  private static final Logger LOG = LoggerFactory.getLogger(BpmnFlowParser.class);

  private static final String RESOURCE_PATH = "bpmn/generator/";

  private static final Map<String, BpmnElementType> ELEMENT_TYPES =
      Map.ofEntries(
          Map.entry("startEvent", BpmnElementType.START_EVENT),
          Map.entry("endEvent", BpmnElementType.END_EVENT),
          Map.entry("serviceTask", BpmnElementType.SERVICE_TASK),
          Map.entry("userTask", BpmnElementType.USER_TASK),
          Map.entry("receiveTask", BpmnElementType.RECEIVE_TASK),
          Map.entry("exclusiveGateway", BpmnElementType.EXCLUSIVE_GATEWAY),
          Map.entry("eventBasedGateway", BpmnElementType.EVENT_BASED_GATEWAY),
          Map.entry("parallelGateway", BpmnElementType.PARALLEL_GATEWAY),
          Map.entry("subProcess", SUB_PROCESS));

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Reads {@code bpmn/generator/<processId>.bpmn} from the classpath, parses its XML, and returns
   * an immutable {@link ProcessGraph} ready for repeated walks.
   *
   * <p>This method is intentionally <em>not</em> cached — the caller controls the lifecycle. Invoke
   * it once per process ID before the instance-generation loop.
   */
  static ProcessGraph parse(final String processId) {
    LOG.info("Parsing BPMN graph for process '{}'.", processId);
    final String path = RESOURCE_PATH + processId + ".bpmn";
    try (final InputStream in = BpmnFlowParser.class.getClassLoader().getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalArgumentException("BPMN resource not found on classpath: " + path);
      }
      final var docBuilder = DocumentBuilderFactory.newNSInstance().newDocumentBuilder();
      final var doc = docBuilder.parse(in);
      doc.getDocumentElement().normalize();

      final NodeList processes = doc.getElementsByTagNameNS("*", "process");
      return nodeListStream(processes)
          .map(Element.class::cast)
          .filter(proc -> processId.equals(proc.getAttribute("id")))
          .findFirst()
          .map(BpmnFlowParser::buildScope)
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "No <process id=\"" + processId + "\"> found in " + path));
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to read BPMN resource: " + path, e);
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to parse BPMN resource: " + path, e);
    }
  }

  // ── Build helpers ─────────────────────────────────────────────────────────

  /**
   * Recursively extracts all flow elements and sequence flows from one BPMN scope (process root or
   * sub-process body) into an immutable {@link ProcessGraph}.
   */
  private static ProcessGraph buildScope(final Element scope) {
    final List<Element> flowElements =
        childElements(scope).filter(el -> ELEMENT_TYPES.containsKey(el.getLocalName())).toList();

    final String startId =
        flowElements.stream()
            .filter(el -> ELEMENT_TYPES.get(el.getLocalName()) == BpmnElementType.START_EVENT)
            .map(el -> el.getAttribute("id"))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No startEvent in scope: " + scope.getAttribute("id")));

    final Map<String, BpmnElementType> types =
        flowElements.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    el -> el.getAttribute("id"), el -> ELEMENT_TYPES.get(el.getLocalName())));

    final Map<String, ProcessGraph> subScopes =
        flowElements.stream()
            .filter(el -> ELEMENT_TYPES.get(el.getLocalName()) == SUB_PROCESS)
            .collect(
                Collectors.toUnmodifiableMap(
                    el -> el.getAttribute("id"), BpmnFlowParser::buildScope));

    final Map<String, List<String>> successors = buildSuccessors(scope, types.keySet());

    return new ProcessGraph(startId, types, successors, subScopes);
  }

  private static Map<String, List<String>> buildSuccessors(
      final Element scope, final Set<String> knownIds) {
    return childElements(scope)
        .filter(el -> "sequenceFlow".equals(el.getLocalName()))
        .filter(flow -> knownIds.contains(flow.getAttribute("sourceRef")))
        .collect(
            Collectors.groupingBy(
                flow -> flow.getAttribute("sourceRef"),
                Collectors.mapping(flow -> flow.getAttribute("targetRef"), Collectors.toList())));
  }

  // ── DOM helpers ───────────────────────────────────────────────────────────

  /** Returns a stream of all child {@link Element} nodes of {@code parent}. */
  private static Stream<Element> childElements(final Element parent) {
    return nodeListStream(parent.getChildNodes())
        .filter(n -> n.getNodeType() == Node.ELEMENT_NODE)
        .map(Element.class::cast);
  }

  /** Converts a {@link NodeList} into an ordered {@link Stream} of {@link Node}s. */
  private static Stream<Node> nodeListStream(final NodeList nodeList) {
    return IntStream.range(0, nodeList.getLength()).mapToObj(nodeList::item);
  }

  private BpmnFlowParser() {}
}
