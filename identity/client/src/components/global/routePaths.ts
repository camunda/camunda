/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export const Paths = {
  setup() {
    return "/setup";
  },
  login() {
    return "/login";
  },
  forbidden() {
    return "/forbidden";
  },
  mappingRules() {
    return "/mapping-rules";
  },
  users() {
    return "/users";
  },
  groups() {
    return "/groups";
  },
  roles() {
    return "/roles";
  },
  tenants() {
    return "/tenants";
  },
  authorizations() {
    return "/authorizations";
  },
  clusterVariables() {
    return "/cluster-variables";
  },
  operationsLog() {
    return "/operations-log";
  },
  taskListeners() {
    return "/task-listeners";
  },
} as const;
