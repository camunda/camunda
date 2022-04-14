/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

// jest-dom adds custom jest matchers for asserting on DOM nodes.
// allows you to do things like:
// expect(element).toHaveTextContent(/react/i)
// learn more: https://github.com/testing-library/jest-dom
import '@testing-library/jest-dom/extend-expect';
import {clearClientCache} from 'modules/apollo-client';
import {mockServer} from 'modules/mockServer';
import {configure} from '@testing-library/react';
import {Textfield as MockTextfield} from 'modules/mocks/common-ui/Textfield';

beforeAll(() => {
  mockServer.listen({
    onUnhandledRequest: 'error',
  });

  Object.defineProperty(window, 'clientConfig', {
    value: {
      ...window.clientConfig,
      canLogout: true,
    },
  });
});

afterEach(async () => {
  mockServer.resetHandlers();
  await clearClientCache();
});
afterAll(() => mockServer.close());

// mock app version
process.env.REACT_APP_VERSION = '1.2.3';

class MockJSONEditor {
  updateText() {}
  destroy() {}
  set() {}
  get() {}
}

jest.mock('jsoneditor', () => MockJSONEditor);
jest.mock('jsoneditor/dist/jsoneditor.css', () => undefined);
jest.mock('@bpmn-io/form-js/dist/assets/form-js.css', () => undefined);
jest.mock('brace/theme/tomorrow_night', () => undefined);
jest.mock('brace/theme/tomorrow', () => undefined);
jest.mock('@camunda-cloud/common-ui-react', () => ({
  ...jest.requireActual('@camunda-cloud/common-ui-react'),
  CmNotificationContainer: () => {
    return null;
  },
  CmTextfield: MockTextfield,
}));

configure({
  asyncUtilTimeout: 7000,
});
