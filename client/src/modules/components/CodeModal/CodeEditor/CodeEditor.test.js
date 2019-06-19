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

const addNewLine = content =>
  `<p className="code-line" data-test="codeline-${0}" key=${0}>${content}</p>`;

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

  it.skip('should render an empty line when no content is passed', () => {
    //given
    let node;
    act(() => {
      node = mountNode(testData.editorMountsWithJSON);
    });

    // when
    node.find('code').simulate('change', {target: {value: ''}});

    // then
    expect(node.find('code').html()).toMatchSnapshot();
  });

  it.skip('should pass sanitized content to parent when content changes', () => {
    //given
    let node;
    act(() => {
      node = mountNode(testData.editorMountsWithString);
    });

    const newString =
      testData.editorMountsWithString.initialValue + 'someAdditionalText';

    //when
    node
      .find('code')
      .simulate('change', {target: {value: addNewLine(newString)}});

    //then
    expect(testData.editorMountsWithString.handleChange).toHaveBeenCalledWith(
      newString
    );
  });
});
