/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Enforces the mapper half of the backward keyset pagination contract.
 *
 * <p>A {@code before} page is answered by seeking against the display direction and reversing the
 * rows back in the reader, so every ORDER BY that shapes such a statement's output has to flip with
 * the paging direction. {@code Commons.orderBy} does that flip once, centrally, and {@link
 * KeySetPaginationOrderByTest} pins it per dialect. This test closes the other half of that
 * argument: it checks that every statement doing keyset pagination actually takes its ordering from
 * that fragment, so a new mapper cannot reintroduce the defect by hardcoding an ORDER BY.
 *
 * <p>A statement that genuinely needs a literal ORDER BY has two ways to say so: guard it with
 * {@code page.searchBefore} and render both directions, or — where the ordering does not shape the
 * paged output at all, such as inside a scalar subquery — mark it with an {@value
 * #EXEMPTION_MARKER} XML comment explaining why.
 */
class KeySetStatementOrderByContractTest {

  static final String EXEMPTION_MARKER = "keyset-order-by-exempt";

  private static final String MAPPER_DIRECTORY = "src/main/resources/mapper";
  private static final String KEY_SET_FILTER_FRAGMENT = "Commons.keySetPageFilter";
  private static final String ORDER_BY_FRAGMENT = "Commons.orderBy";
  private static final String BACKWARD_FLAG = "searchBefore";

  /**
   * Guards the discovery itself: if the scan stops finding statements — a renamed fragment, a moved
   * directory — the contract would be vacuously satisfied.
   */
  private static final int MINIMUM_EXPECTED_STATEMENTS = 30;

  @Test
  void shouldRenderOrderByFromTheSharedFragmentInEveryKeySetStatement() throws Exception {
    // given
    final var statements = keySetStatements();

    // then
    assertThat(statements)
        .as("keyset-paginated select statements found under %s", MAPPER_DIRECTORY)
        .hasSizeGreaterThanOrEqualTo(MINIMUM_EXPECTED_STATEMENTS);

    final var softly = new SoftAssertions();
    for (final var statement : statements) {
      softly
          .assertThat(statement.unguardedLiteralOrderBys())
          .as(
              "%s hardcodes an ORDER BY. It has to come from %s, be guarded by page.%s, or carry an"
                  + " '%s' XML comment saying why the paging direction does not apply to it.",
              statement.id(), ORDER_BY_FRAGMENT, BACKWARD_FLAG, EXEMPTION_MARKER)
          .isEmpty();
      softly
          .assertThat(statement.hasOrdering())
          .as(
              "%s paginates by keyset but renders no ORDER BY, so its page boundaries are undefined",
              statement.id())
          .isTrue();
    }
    softly.assertAll();
  }

  private static List<KeySetStatement> keySetStatements() throws Exception {
    final var statements = new ArrayList<KeySetStatement>();
    for (final var mapperFile : mapperFiles()) {
      final var mapper = parse(mapperFile);
      final var namespace = mapper.getAttribute("namespace");
      final var fragments = sqlFragmentsById(mapper);

      for (final var select : childElements(mapper, "select")) {
        // a databaseId-specific variant is a copy of the same statement for another vendor and is
        // checked in its own right, so both are visited and reported under distinguishable ids
        final var databaseId = select.getAttribute("databaseId");
        final var id =
            namespace
                + "."
                + select.getAttribute("id")
                + (databaseId.isEmpty() ? "" : " [" + databaseId + "]");

        final var scan = new StatementScan(fragments);
        scan.walk(select, false, null);
        if (scan.usesKeySetPagination) {
          statements.add(
              new KeySetStatement(
                  id,
                  scan.usesOrderByFragment || scan.hasDirectionAwareOrderBy,
                  scan.unguardedLiteralOrderBys));
        }
      }
    }
    return statements;
  }

  private static boolean containsOrderBy(final String text) {
    return text.toUpperCase(Locale.ROOT).contains("ORDER BY");
  }

  private static String squash(final String text) {
    return text.replaceAll("\\s+", " ").trim();
  }

  private static Map<String, Element> sqlFragmentsById(final Element mapper) {
    return childElements(mapper, "sql").stream()
        .collect(
            Collectors.toMap(
                fragment -> fragment.getAttribute("id"), fragment -> fragment, (a, b) -> a));
  }

  private static List<File> mapperFiles() {
    final var files = new File(MAPPER_DIRECTORY).listFiles((dir, name) -> name.endsWith(".xml"));
    assertThat(files).as("mapper files under %s", MAPPER_DIRECTORY).isNotNull().isNotEmpty();
    return List.of(files);
  }

  private static Element parse(final File mapperFile) throws Exception {
    final var factory = DocumentBuilderFactory.newInstance();
    // the mybatis DTD is only referenced here, never needed, and must never be fetched
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setIgnoringComments(false);

    final var builder = factory.newDocumentBuilder();
    builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
    return builder.parse(mapperFile).getDocumentElement();
  }

  private static List<Element> childElements(final Node parent, final String tagName) {
    return childNodes(parent).stream()
        .filter(Element.class::isInstance)
        .map(Element.class::cast)
        .filter(element -> tagName.equals(element.getTagName()))
        .toList();
  }

  private static List<Node> childNodes(final Node parent) {
    final NodeList children = parent.getChildNodes();
    return IntStream.range(0, children.getLength()).mapToObj(children::item).toList();
  }

  /**
   * A select that pulls in {@code Commons.keySetPageFilter}, with everything the contract needs to
   * be checked: whether it orders at all, and which literal ORDER BYs are neither direction-aware
   * nor exempt.
   */
  private record KeySetStatement(
      String id, boolean hasOrdering, List<String> unguardedLiteralOrderBys) {}

  /**
   * Walks a statement in document order, following {@code <include>}s into the {@code <sql>}
   * fragments of the same file, and records what the statement does about ordering.
   */
  private static final class StatementScan {

    private final Map<String, Element> localFragments;
    private final Set<String> visitedFragments = new HashSet<>();

    private boolean usesKeySetPagination;
    private boolean usesOrderByFragment;
    private boolean hasDirectionAwareOrderBy;
    private final List<String> unguardedLiteralOrderBys = new ArrayList<>();

    private StatementScan(final Map<String, Element> localFragments) {
      this.localFragments = localFragments;
    }

    /**
     * @param directionAware whether an enclosing element branches on the paging direction
     * @param exemption the most recent XML comment, which may exempt the next literal ORDER BY
     */
    private void walk(final Node node, final boolean directionAware, final String exemption) {
      var pendingExemption = exemption;

      for (final var child : childNodes(node)) {
        switch (child.getNodeType()) {
          case Node.COMMENT_NODE -> pendingExemption = child.getNodeValue();
          case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
            final var text = child.getNodeValue();
            if (containsOrderBy(text)) {
              if (directionAware) {
                hasDirectionAwareOrderBy = true;
              } else if (pendingExemption == null || !pendingExemption.contains(EXEMPTION_MARKER)) {
                unguardedLiteralOrderBys.add(squash(text));
              }
            }
          }
          case Node.ELEMENT_NODE -> {
            final var element = (Element) child;
            if ("include".equals(element.getTagName())) {
              visitInclude(element, directionAware, pendingExemption);
            } else {
              walk(element, directionAware || branchesOnPagingDirection(element), pendingExemption);
            }
          }
          default -> {
            // nothing else affects the rendered SQL
          }
        }
      }
    }

    private void visitInclude(
        final Element include, final boolean directionAware, final String exemption) {
      final var refId = include.getAttribute("refid");
      if (refId.endsWith(KEY_SET_FILTER_FRAGMENT)) {
        usesKeySetPagination = true;
      } else if (refId.endsWith(ORDER_BY_FRAGMENT)) {
        usesOrderByFragment = true;
      }

      // follow same-file fragments so a statement cannot hide either half behind an <sql> include
      final var localName = refId.substring(refId.lastIndexOf('.') + 1);
      final var fragment = localFragments.get(localName);
      if (fragment != null && visitedFragments.add(localName)) {
        walk(fragment, directionAware, exemption);
      }
    }

    /**
     * True for an element that renders only for one paging direction — either it tests the flag
     * itself, or it is the {@code <choose>} whose branches split on it, which also covers the
     * {@code <otherwise>} carrying the forward ordering.
     */
    private static boolean branchesOnPagingDirection(final Element element) {
      if (element.getAttribute("test").contains(BACKWARD_FLAG)) {
        return true;
      }
      return "choose".equals(element.getTagName())
          && childElements(element, "when").stream()
              .anyMatch(when -> when.getAttribute("test").contains(BACKWARD_FLAG));
    }
  }
}
