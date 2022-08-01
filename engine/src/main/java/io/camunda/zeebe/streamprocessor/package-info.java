/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
/**
 * This package is to isolate classes that will be part of the platform. This package is needed as
 * an intermediary step during engine abstraction refactoring to make the change easier. Attempts
 * were made to create a new Maven Module and start moving classes there. This did work well for the
 * sources, but not for the test sources. The test sources can best be refactored after the engine
 * abstraction was fully introduced.
 */
package io.camunda.zeebe.streamprocessor;
