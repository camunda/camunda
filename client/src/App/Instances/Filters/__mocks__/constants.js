/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const constants = jest.genMockFromModule('../constants');

// lower debounce delay in tests to save time
export const DEBOUNCE_DELAY = 200;

export const {ALL_VERSIONS_OPTION, DEFAULT_CONTROLLED_VALUES} = constants;
