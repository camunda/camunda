/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const brandLink = Selector('[data-test="header-link-brand"]');
export const dashboardLink = Selector('[data-test="header-link-dashboard"]');
export const instancesLink = Selector('[data-test="header-link-instances"]');
export const instancesBadge = Selector(
  '[data-test="header-link-instances"] [data-test="badge"]'
);
export const filtersLink = Selector('[data-test="header-link-filters"]');
export const incidentsLink = Selector('[data-test="header-link-incidents"]');
export const selectionsLink = Selector('[data-test="header-link-selections"]');
export const userDropdown = Selector('[data-test="dropdown-toggle"]');
export const logoutItem = Selector('[data-test="menu"] > li:nth-child(2)');
