/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.rdbms;

import java.util.List;

/** Where the schema of the mailbox and the leases is defined, and what it consists of. */
public final class RdbmsMailboxSchema {

  /** The Liquibase changelog that creates the tables. */
  public static final String CHANGELOG = "db/changelog/mailbox/changelog-master.xml";

  /** The tables of the changelog. */
  public static final List<String> TABLES =
      List.of(
          "CAM_RU_PARTITION_LEASE",
          "CAM_RU_MAILBOX_INBOX",
          "CAM_RU_MAILBOX_OUTBOX",
          "CAM_RU_PARTITION_LEADER");

  static final List<String> MAPPERS =
      List.of("db/mybatis/mailbox/MailboxMapper.xml", "db/mybatis/mailbox/LeaseMapper.xml");

  private RdbmsMailboxSchema() {}
}
