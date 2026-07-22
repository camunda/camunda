/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Discovers and parses {@code .bpmn} files backing the {@code /process-map} page (see {@link
 * LakeUiServer}'s "Process map" section) — a plain JDK DOM parse, no {@code camunda-bpmn-model}
 * dependency, since all this needs is process ids, element ids/types/names and sequence-flow
 * source/target refs.
 *
 * <h2>Directory resolution order</h2>
 *
 * <ol>
 *   <li>{@code lake.bpmnDir} (an explicit {@link io.camunda.analytics.lake.LakeConfig#bpmnDir()}) —
 *       if set, this directory alone is scanned (recursively), even if it turns out to contain no
 *       {@code .bpmn} files. No further fallback.
 *   <li>{@code /tmp/eb-demo} (recursively) — where this module's own demo stack (see the module
 *       README's "Running it against a local stack" section) stages a running deployment's files.
 *   <li>The repository's {@code analytics/} directory (recursively) — the location named directly
 *       in this feature's design brief.
 *   <li>Two further PoC-pragmatic fallbacks, added because neither of the two locations above
 *       actually holds any {@code .bpmn} file for the demo stack's own processes at the time this
 *       was written (see {@link #findRepoRoot()}'s javadoc): {@code
 *       load-tests/load-tester/src/main/resources/bpmn/realistic} (the models {@code
 *       analytics/run-realistic-load.sh} launches) and {@code
 *       event-bridge/event-bridge-examples/src/main/resources} (where {@code
 *       region-exec-time-demo.bpmn} — the one statically-authored model {@code
 *       MultiProcessDemoDriver} deploys — actually lives; that driver's other four processes are
 *       built with the {@code Bpmn.createExecutableProcess(...)} Java DSL and have no {@code .bpmn}
 *       file on disk at all, so they can never appear in this catalog).
 * </ol>
 *
 * <p>Each candidate directory is tried in order; the first one that is an existing directory AND
 * yields at least one {@code .bpmn} file wins (its result is returned as-is, including zero matches
 * against the data's process ids — that is a legitimate "nothing lines up" outcome, not a reason to
 * keep searching). {@link ScanResult#scannedDirs()} always lists every directory that was actually
 * tried (existing or not), so the {@code /process-map} page's "no match" hint can show the engineer
 * exactly where this looked.
 */
final class BpmnCatalog {

  private static final Logger LOG = LoggerFactory.getLogger(BpmnCatalog.class);

  private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";

  private BpmnCatalog() {}

  /**
   * Runs the directory-resolution order described in the class javadoc and parses every {@code
   * .bpmn} file found in the winning directory. Never throws: a file that fails to parse is logged
   * and skipped, and a missing/inaccessible directory simply yields no models.
   */
  static ScanResult discover(final Path explicitOverride) {
    final List<Path> candidates = candidateDirs(explicitOverride);
    final List<Path> scannedDirs = new ArrayList<>();
    for (final Path candidate : candidates) {
      scannedDirs.add(candidate);
      final List<Path> bpmnFiles = findBpmnFiles(candidate);
      if (!bpmnFiles.isEmpty()) {
        return new ScanResult(List.copyOf(scannedDirs), parseAll(bpmnFiles));
      }
      if (explicitOverride != null) {
        // An explicit override never falls through to the defaults below, even when empty.
        break;
      }
    }
    return new ScanResult(List.copyOf(scannedDirs), List.of());
  }

  private static List<Path> candidateDirs(final Path explicitOverride) {
    if (explicitOverride != null) {
      return List.of(explicitOverride);
    }
    final List<Path> candidates = new ArrayList<>();
    candidates.add(Path.of("/tmp/eb-demo"));
    final Path repoRoot = findRepoRoot();
    if (repoRoot != null) {
      candidates.add(repoRoot.resolve("analytics"));
      candidates.add(repoRoot.resolve("load-tests/load-tester/src/main/resources/bpmn/realistic"));
      candidates.add(repoRoot.resolve("event-bridge/event-bridge-examples/src/main/resources"));
    }
    return candidates;
  }

  /**
   * Walks up from the JVM's working directory looking for the monorepo root — recognized as the
   * first ancestor that has {@code analytics/}, {@code load-tests/} and {@code event-bridge/} all
   * as direct subdirectories. A PoC-pragmatic heuristic (no build marker file is consulted): this
   * module is always run from somewhere inside the monorepo checkout (see the module README's run
   * instructions), so climbing far enough always finds it. Returns {@code null} if no such ancestor
   * exists (e.g. this jar ends up running from a location entirely outside the checkout) — callers
   * then simply skip the two candidates that need it.
   */
  private static Path findRepoRoot() {
    Path dir = Path.of("").toAbsolutePath();
    while (dir != null) {
      if (Files.isDirectory(dir.resolve("analytics"))
          && Files.isDirectory(dir.resolve("load-tests"))
          && Files.isDirectory(dir.resolve("event-bridge"))) {
        return dir;
      }
      dir = dir.getParent();
    }
    return null;
  }

  private static List<Path> findBpmnFiles(final Path dir) {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> walk = Files.walk(dir)) {
      return walk.filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().endsWith(".bpmn"))
          .sorted()
          .toList();
    } catch (final IOException e) {
      LOG.debug("Failed to scan {} for .bpmn files: {}", dir, e.getMessage());
      return List.of();
    }
  }

  private static List<BpmnModel> parseAll(final List<Path> files) {
    final List<BpmnModel> models = new ArrayList<>();
    for (final Path file : files) {
      try {
        models.addAll(parseFile(file));
      } catch (final RuntimeException e) {
        LOG.warn("Failed to parse BPMN file {}; skipping it", file, e);
      }
    }
    return models;
  }

  /** One file can carry more than one {@code <bpmn:process>} (e.g. call-activity siblings). */
  private static List<BpmnModel> parseFile(final Path file) {
    final Document doc = parseXml(file);
    final NodeList processNodes = doc.getElementsByTagNameNS(BPMN_NS, "process");
    final List<BpmnModel> models = new ArrayList<>(processNodes.getLength());
    for (int i = 0; i < processNodes.getLength(); i++) {
      final Element processEl = (Element) processNodes.item(i);
      final String processId = processEl.getAttribute("id");
      if (processId.isBlank()) {
        continue;
      }
      final String processName = processEl.getAttribute("name");
      final Map<String, ElementInfo> elements = new LinkedHashMap<>();
      final List<SequenceFlowInfo> flows = new ArrayList<>();
      collectElements(processEl, elements, flows);
      models.add(new BpmnModel(file, processId, processName, elements, flows));
    }
    return models;
  }

  /** Depth-first walk collecting every id-bearing BPMN element and every sequence flow. */
  private static void collectElements(
      final Element parent,
      final Map<String, ElementInfo> elements,
      final List<SequenceFlowInfo> flows) {
    final NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      final Node node = children.item(i);
      if (!(node instanceof final Element child) || !BPMN_NS.equals(child.getNamespaceURI())) {
        continue;
      }
      final String id = child.getAttribute("id");
      final String localName = child.getLocalName();
      if ("sequenceFlow".equals(localName)) {
        final String sourceRef = child.getAttribute("sourceRef");
        final String targetRef = child.getAttribute("targetRef");
        if (!id.isBlank() && !sourceRef.isBlank() && !targetRef.isBlank()) {
          flows.add(new SequenceFlowInfo(id, sourceRef, targetRef));
        }
      } else if (!id.isBlank()) {
        elements.put(id, new ElementInfo(id, child.getAttribute("name"), localName));
      }
      // Recurse -- covers elements nested under e.g. subProcess/transaction.
      collectElements(child, elements, flows);
    }
  }

  private static Document parseXml(final Path file) {
    try {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      // Hardening: this only ever reads locally-authored trusted files, but disabling external
      // entity resolution costs nothing and is the right default for any XML parser.
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      final DocumentBuilder builder = factory.newDocumentBuilder();
      return builder.parse(file.toFile());
    } catch (final ParserConfigurationException | SAXException | IOException e) {
      throw new IllegalStateException("Failed to parse BPMN file " + file, e);
    }
  }

  /**
   * Matches {@code models} (from {@link #discover}) against the process ids actually present in the
   * data, in file order, first model per process id winning if more than one file declares the same
   * process id.
   */
  static List<BpmnModel> matching(final List<BpmnModel> models, final Set<String> dataProcessIds) {
    final Map<String, BpmnModel> byProcessId = new LinkedHashMap<>();
    for (final BpmnModel model : models) {
      if (dataProcessIds.contains(model.processId())) {
        byProcessId.putIfAbsent(model.processId(), model);
      }
    }
    return List.copyOf(byProcessId.values());
  }

  /** One {@code <bpmn:process>} definition parsed out of a {@code .bpmn} file. */
  record BpmnModel(
      Path file,
      String processId,
      String processName,
      Map<String, ElementInfo> elementsById,
      List<SequenceFlowInfo> sequenceFlows) {}

  /** One id-bearing BPMN element (anything except a sequence flow). */
  record ElementInfo(String id, String name, String type) {}

  /** One {@code <bpmn:sequenceFlow>}. */
  record SequenceFlowInfo(String id, String sourceRef, String targetRef) {}

  /** Outcome of {@link #discover(Path)}. */
  record ScanResult(List<Path> scannedDirs, List<BpmnModel> models) {}
}
