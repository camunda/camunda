/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {act} from 'react-dom/test-utils';
import {mount} from 'enzyme';

import {testData} from './CodeEditor.setup';

import {ThemeProvider} from 'modules/theme';

import CodeModal from './CodeEditor';

function mountNode(props = {}) {
  return mount(
    <ThemeProvider>
      <CodeModal {...props} />
    </ThemeProvider>
  );
}

describe('CodeModal', () => {
  beforeEach(() => {
    testData.editorMountsWithString.handleChange.mockClear();
  });

  it('should prettify content when initially rendered', () => {
    //given
    const contentTypes = [
      testData.editorMountsWithString,
      testData.editorMountsWithJSON,
      testData.editorMountsWithArray,
      testData.editorMountsWithBrokenJSON,
      testData.editorMountsWithNullValue
    ];

    let node;
    contentTypes.forEach(contentType => {
      //when
      act(() => {
        node = mountNode(contentType);
      });

      // then
      expect(node.find('code').html()).toMatchSnapshot();
    });
  });
});
