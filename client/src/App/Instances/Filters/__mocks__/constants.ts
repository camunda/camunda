/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const constants = jest.genMockFromModule('../constants');

// lower debounce delay in tests to save time
export const DEBOUNCE_DELAY = 200;

// @ts-expect-error ts-migrate(2339) FIXME: Property 'ALL_VERSIONS_OPTION' does not exist on t... Remove this comment to see the full error message
export const {ALL_VERSIONS_OPTION, DEFAULT_CONTROLLED_VALUES} = constants;
