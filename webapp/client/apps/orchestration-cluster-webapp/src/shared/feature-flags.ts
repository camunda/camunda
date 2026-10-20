/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const ENABLE_PROCESS_MIGRATION = true;
// Enable once token staging (#58011) and the modification summary (#56037) land.
const ENABLE_PROCESS_INSTANCE_MODIFICATION = false;

export {ENABLE_PROCESS_MIGRATION, ENABLE_PROCESS_INSTANCE_MODIFICATION};
