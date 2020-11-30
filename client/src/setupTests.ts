/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

// jest-dom adds custom jest matchers for asserting on DOM nodes.
// allows you to do things like:
// expect(element).toHaveTextContent(/react/i)
// learn more: https://github.com/testing-library/jest-dom
import '@testing-library/jest-dom/extend-expect';
import {mockServer} from 'modules/mockServer';

beforeAll(() =>
  mockServer.listen({
    onUnhandledRequest(req) {
      console.error(
        'Found an unhandled %s request to %s',
        req.method,
        req.url.href,
      );
    },
  }),
);
afterEach(() => mockServer.resetHandlers());
afterAll(() => mockServer.close());

jest.mock('@camunda-cloud/common-ui-react', () => ({
  CmNotificationContainer: () => {
    return null;
  },
}));
