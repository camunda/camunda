/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export const countStoreEmpty = {
  running: 0,
  active: 0,
  withIncidents: 0,
  isLoaded: true
};

export const countStoreComplete = {
  running: 23,
  active: 12,
  withIncidents: 11,
  isLoaded: true
};

export const countStoreWithoutIncidents = {
  running: 23,
  active: 12,
  withIncidents: 0,
  isLoaded: true
};

export const countStoreLoading = {
  running: 0,
  active: 0,
  withIncidents: 0,
  isLoaded: false
};
