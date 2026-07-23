/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {storePostLoginRedirect, restorePostLoginRedirect} from './postLoginRedirect';

const STORAGE_KEY = 'optimizePostLoginRedirect';

beforeEach(() => {
  sessionStorage.clear();
  window.location.hash = '';
});

describe('storePostLoginRedirect', () => {
  it('stashes a real route', () => {
    storePostLoginRedirect('#/report/123');
    expect(sessionStorage.getItem(STORAGE_KEY)).toBe('#/report/123');
  });

  it('ignores the home route', () => {
    storePostLoginRedirect('#/');
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('ignores the logout route', () => {
    storePostLoginRedirect('#/logout');
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('reads the current hash by default', () => {
    window.location.hash = '#/dashboard/9';
    storePostLoginRedirect();
    expect(sessionStorage.getItem(STORAGE_KEY)).toBe('#/dashboard/9');
  });
});

describe('restorePostLoginRedirect', () => {
  it('applies the stashed route when landing on home and clears it', () => {
    sessionStorage.setItem(STORAGE_KEY, '#/report/123');

    restorePostLoginRedirect();

    expect(window.location.hash).toBe('#/report/123');
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('does not override a route the browser already preserved, but clears the stash', () => {
    sessionStorage.setItem(STORAGE_KEY, '#/report/123');
    window.location.hash = '#/dashboard/9';

    restorePostLoginRedirect();

    expect(window.location.hash).toBe('#/dashboard/9');
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('does nothing when nothing is stashed', () => {
    window.location.hash = '#/report/123';

    restorePostLoginRedirect();

    expect(window.location.hash).toBe('#/report/123');
  });
});
