/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/*
 * Conventions for the Camunda clients under `clients/`.
 *
 * The Maven clients inherit `camunda-library-parent`, which today adds no dependency management of
 * its own and simply inherits `zeebe-parent`. This plugin therefore only pulls in the shared
 * `parent-conventions`; it exists as the single place to apply client-wide overrides.
 */

plugins { id("buildlogic.parent-conventions") }
