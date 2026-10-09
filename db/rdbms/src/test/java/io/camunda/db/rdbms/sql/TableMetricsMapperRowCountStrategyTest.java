/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Guards each vendor's {@code tableRowCount.strategy} property against drifting from what {@code
 * TableMetricsMapper.xml} actually declares for that vendor.
 */
class TableMetricsMapperRowCountStrategyTest {

  private static final Set<String> KNOWN_VENDOR_IDS =
      Set.of("postgresql", "oracle", "mysql", "mariadb", "mssql", "h2");

  @Test
  void shouldAgreeWithMapperXmlOnWhichVendorsAreBatched() throws Exception {
    // given
    final var vendorsWithBatchedBranch = countTableRowsDatabaseIdsFromMapperXml();
    assertThat(vendorsWithBatchedBranch)
        .as("sanity check: the mapper XML should still declare some batched branches")
        .isNotEmpty();

    // then
    for (final String databaseId : KNOWN_VENDOR_IDS) {
      final var properties = VendorDatabasePropertiesLoader.load(databaseId);
      final var expectedBatched = vendorsWithBatchedBranch.contains(databaseId);
      assertThat(properties.usesCatalogRowCountStatistics())
          .as(
              "%s.properties' tableRowCount.strategy should agree with whether"
                  + " TableMetricsMapper.xml has a countTableRows branch for databaseId=\"%s\""
                  + " (it does: %s)",
              databaseId, databaseId, expectedBatched)
          .isEqualTo(expectedBatched);
    }
  }

  @Test
  void shouldDeclareTheSingleTableStatementBackingLiveVendors() throws Exception {
    // given
    final var liveVendors = new HashSet<String>();
    for (final String databaseId : KNOWN_VENDOR_IDS) {
      if (!VendorDatabasePropertiesLoader.load(databaseId).usesCatalogRowCountStatistics()) {
        liveVendors.add(databaseId);
      }
    }
    assertThat(liveVendors)
        .as("sanity check: at least one vendor should still use the live-count strategy")
        .isNotEmpty();

    // when
    final var statementIds = selectIdsFromMapperXml();

    // then - countSingleTableRows carries no databaseId, so the invariant above cannot see it
    assertThat(statementIds)
        .as(
            "%s use tableRowCount.strategy=live, which RdbmsTableRowCountProvider serves with"
                + " TableMetricsMapper#countSingleTableRows",
            liveVendors)
        .contains("countSingleTableRows");
  }

  private static Set<String> selectIdsFromMapperXml() throws Exception {
    final var ids = new HashSet<String>();
    forEachSelect(select -> ids.add(select.getAttribute("id")));
    return Set.copyOf(ids);
  }

  private static Set<String> countTableRowsDatabaseIdsFromMapperXml() throws Exception {
    final var databaseIds = new HashSet<String>();
    forEachSelect(
        select -> {
          if ("countTableRows".equals(select.getAttribute("id"))
              && select.hasAttribute("databaseId")) {
            databaseIds.add(select.getAttribute("databaseId"));
          }
        });
    return Set.copyOf(databaseIds);
  }

  private static void forEachSelect(final Consumer<Element> consumer) throws Exception {
    final var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    final var builder = factory.newDocumentBuilder();
    // Never resolve the DOCTYPE's external DTD over the network.
    builder.setEntityResolver(
        (publicId, systemId) -> new InputSource(new ByteArrayInputStream(new byte[0])));

    try (InputStream in = mapperXml()) {
      final var document = builder.parse(in);
      final NodeList selects = document.getElementsByTagName("select");
      for (int i = 0; i < selects.getLength(); i++) {
        consumer.accept((Element) selects.item(i));
      }
    }
  }

  private static InputStream mapperXml() throws IOException {
    final var in =
        TableMetricsMapperRowCountStrategyTest.class
            .getClassLoader()
            .getResourceAsStream("mapper/TableMetricsMapper.xml");
    if (in == null) {
      throw new IOException("mapper/TableMetricsMapper.xml not found on the test classpath");
    }
    return in;
  }
}
