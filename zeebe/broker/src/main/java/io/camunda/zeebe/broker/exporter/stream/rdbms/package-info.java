/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * The exporter state in the relational database of the primary storage, so that a broker that takes
 * a partition over continues each exporter where its predecessor stopped.
 */
@NullMarked
package io.camunda.zeebe.broker.exporter.stream.rdbms;

import org.jspecify.annotations.NullMarked;
