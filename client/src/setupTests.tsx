/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import 'jest-styled-components';
import '@testing-library/jest-dom';
import {mockServer} from 'modules/mock-server/node';
import {configure} from '@testing-library/react';
import React, {useImperativeHandle, useState} from 'react';
import {CmTextfield} from '@camunda-cloud/common-ui-react';

class MockJSONEditor {
  updateText() {}
  destroy() {}
  set() {}
  get() {}
}

type Props = {
  validation: React.ComponentProps<typeof CmTextfield>['validation'];
  validationStyle: React.ComponentProps<typeof CmTextfield>['validationStyle'];
  onCmInput: React.ChangeEventHandler<HTMLInputElement>;
  fieldSuffix: React.ComponentProps<typeof CmTextfield>['fieldSuffix'];
  shouldDebounceError: boolean;
};

const MockTextField = React.forwardRef<any, Props>(
  (
    {
      children,
      validation,
      fieldSuffix,
      validationStyle,
      onCmInput,
      shouldDebounceError,
      ...props
    },
    ref
  ) => {
    const [errorMessage, setErrorMessage] = useState<string | undefined>(
      undefined
    );

    const renderValidity = async () => {
      if (validation?.type === 'custom') {
        const result = await validation.validator('');
        return result.isValid
          ? setErrorMessage(undefined)
          : setErrorMessage(result.message);
      }
    };

    useImperativeHandle(ref, () => ({
      renderValidity,
      forceFocus: () => {},
    }));

    return (
      <>
        <input {...props} onChange={onCmInput} ref={ref} />
        <div>{errorMessage}</div>
        {fieldSuffix?.type === 'icon' && (
          <div title="open json editor modal" onClick={fieldSuffix.press}></div>
        )}
      </>
    );
  }
);

jest.mock('jsoneditor', () => MockJSONEditor);
jest.mock('jsoneditor/dist/jsoneditor.css', () => undefined);
jest.mock('brace/theme/tomorrow_night', () => undefined);
jest.mock('brace/theme/tomorrow', () => undefined);
jest.mock('modules/utils/date/formatDate');
jest.mock('@camunda-cloud/common-ui-react', () => {
  const React = require('react');

  return {
    ...jest.requireActual('@camunda-cloud/common-ui-react'),
    CmNotificationContainer: React.forwardRef(
      function CmNotificationContainer() {
        return null;
      }
    ),
    CmTextfield: MockTextField,
  };
});

jest.mock('modules/components/InfiniteScroller', () => {
  const InfiniteScroller: React.FC = ({children}) => {
    return <>{children}</>;
  };
  return {InfiniteScroller};
});

global.beforeEach(() => {
  localStorage.clear();

  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: jest.fn().mockImplementation(() => ({
      matches: false,
    })),
  });
});

const localStorageMock = (function () {
  let store: {[key: string]: string} = {};
  return {
    getItem(key: string) {
      return store[key];
    },
    setItem(key: string, value: string) {
      store[key] = value;
    },
    clear() {
      store = {};
    },
    removeItem: jest.fn(),
  };
})();

Object.defineProperty(window, 'localStorage', {value: localStorageMock});

window.MutationObserver = MutationObserver;

beforeAll(() => mockServer.listen());
afterEach(() => mockServer.resetHandlers());
afterAll(() => mockServer.close());

configure({
  asyncUtilTimeout: 7000,
});
