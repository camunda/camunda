/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {track} from 'tracking';
import {addNotification} from 'notifications';

import TextReport from './TextReport';

jest.mock('tracking', () => ({track: jest.fn()}));
jest.mock('notifications', () => ({addNotification: jest.fn()}));

const editorValue = {
  root: {
    children: [
      {
        children: [
          {
            detail: 0,
            format: 0,
            mode: 'normal',
            style: '',
            text: 'some text',
            type: 'text',
            version: 1,
          },
        ],
        direction: 'ltr',
        format: '',
        indent: 0,
        type: 'paragraph',
        version: 1,
      },
    ],
    direction: 'ltr',
    format: '',
    indent: 0,
    type: 'root',
    version: 1,
  },
};

it('should include an editor with rendered content', () => {
  const node = shallow(<TextReport report={{configuration: {text: editorValue}}} />);

  const editor = node.find('TextEditor');

  expect(editor).toExist();
  expect(editor.prop('initialValue')).toEqual(editorValue);
});

it('should update the key to reload it when loadReportData function is called', async () => {
  const node = shallow(
    <TextReport
      report={{configuration: {text: editorValue}}}
      children={(props) => <p {...props}>child</p>}
    />
  );

  node.find('p').prop('loadReportData')();

  expect(node.find('TextEditor').key()).toBe('1');
});

it('should return null when no text is provided', () => {
  const node = shallow(<TextReport report={{configuration: {something: ''}}} />);

  expect(node.find('.TextReport')).not.toExist();
});

it('should add notification and sent mixpanel event on edit', async () => {
  const node = shallow(
    <TextReport report={{configuration: {text: editorValue}}} children={() => <p>child</p>} />
  );

  node.find('.EditTextReport').simulate('click');

  expect(track).toHaveBeenCalledWith('editTextReport');
  expect(addNotification).toHaveBeenCalled();
});

describe('TextReport.isTextReport', () => {
  it('should return true if report is text', () => {
    expect(TextReport.isTextReport({configuration: {text: 'text'}})).toBe(true);
  });

  it('should return false if report is not text', () => {
    expect(TextReport.isTextReport({configuration: {external: 'externalUrl'}})).toBe(false);
  });
});
