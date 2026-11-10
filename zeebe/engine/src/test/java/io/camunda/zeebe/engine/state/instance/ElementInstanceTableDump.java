/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs the committed element instance rows of a partition in readable form, with the msgpack
 * payload decoded to JSON. Logs at DEBUG through its own logger, which {@code log4j2-test.xml}
 * keeps at INFO unless {@code -Dzeebe.engine.rdbms.dumpLevel=debug} is set.
 */
final class ElementInstanceTableDump {

  private static final Logger LOG = LoggerFactory.getLogger(ElementInstanceTableDump.class);

  private static final String SELECT_ROWS =
      "SELECT element_instance_key, parent_key, process_instance_key, process_definition_key,"
          + " bpmn_element_type, state, payload FROM element_instance"
          + " WHERE partition_id = ? ORDER BY element_instance_key";

  private ElementInstanceTableDump() {}

  static void logRows(final DataSource dataSource, final int partitionId) {
    if (!LOG.isDebugEnabled()) {
      return;
    }

    final var dump = new StringBuilder();
    try (final var connection = dataSource.getConnection();
        final var statement = connection.prepareStatement(SELECT_ROWS)) {
      statement.setInt(1, partitionId);
      try (final var rows = statement.executeQuery()) {
        while (rows.next()) {
          dump.append(
              String.format(
                  "%n  key=%d parent=%d processInstance=%d processDefinition=%d type=%s state=%s%n"
                      + "    payload=%s",
                  rows.getLong(1),
                  rows.getLong(2),
                  rows.getLong(3),
                  rows.getLong(4),
                  BpmnElementType.values()[rows.getShort(5)],
                  ProcessInstanceIntent.from(rows.getShort(6)),
                  MsgPackConverter.convertToJson(rows.getBytes(7))));
        }
      }
    } catch (final SQLException e) {
      LOG.debug("Failed to dump element instance rows of partition {}", partitionId, e);
      return;
    }

    LOG.debug(
        "Committed element instance rows of partition {}:{}",
        partitionId,
        dump.isEmpty() ? " none" : dump);
  }
}
