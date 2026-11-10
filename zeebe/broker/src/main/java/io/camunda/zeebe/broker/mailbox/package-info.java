/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * How the broker gets the cross-region mailbox. The broker does not know a database. A provider on
 * the class path supplies the store, and the broker uses the mailbox only if a provider returns
 * one.
 */
@NullMarked
package io.camunda.zeebe.broker.mailbox;

import org.jspecify.annotations.NullMarked;
