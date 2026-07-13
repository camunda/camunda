/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.recover;

import picocli.CommandLine.Command;

/**
 * Command group for recovering data in secondary storage (ES/OS) from Zeebe primary storage
 * (RocksDB), for disaster recovery when secondary-storage documents are lost.
 */
@Command(
    name = "recover",
    description = "Recover secondary-storage data from a Zeebe primary-storage (RocksDB) snapshot",
    subcommands = {RecoverProcessDefinitionsCommand.class})
public class RecoverCommand {}
