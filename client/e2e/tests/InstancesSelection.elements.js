/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const instanceCheckbox = Selector('[data-test="instance-checkbox"]');
export const headerLinkInstances = Selector(
  '[data-test="header-link-instances"]'
);
export const headerLinkIncidents = Selector(
  '[data-test="header-link-incidents"]'
);
export const createOperationDropdown = Selector('div').withText(
  'Apply Operation on 1 Instance'
);

export const errorMessageFilter = Selector('[data-test="error-message"]');
export const nextPage = Selector('[data-test="next-page"]');
export const sortByWorkflowName = Selector(
  '[data-test="sort-by-workflowName"]'
);
