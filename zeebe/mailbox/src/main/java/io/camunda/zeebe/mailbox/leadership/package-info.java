/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Leader election with leases in the database: claims with a compare-and-set on the term, renewals
 * with a counter, failure detection by an unchanged counter, and self-fencing.
 */
@NullMarked
package io.camunda.zeebe.mailbox.leadership;

import org.jspecify.annotations.NullMarked;
