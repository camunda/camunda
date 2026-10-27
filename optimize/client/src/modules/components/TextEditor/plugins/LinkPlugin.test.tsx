/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {LexicalEditor} from 'lexical';
import {InsertLinkModal} from './LinkPlugin';

const editor = {
  dispatchCommand: jest.fn(),
} as unknown as LexicalEditor;

it('should display the link modal', () => {
  const node = shallow(<InsertLinkModal editor={editor} />);

  expect(node).toExist();
});

it('should call the onClose when modal is closed', () => {
  const spy = jest.fn();
  const node = shallow(<InsertLinkModal editor={editor} onClose={spy} />);

  node.find('Modal').prop<() => void>('onClose')();

  expect(spy).toHaveBeenCalled();

  node.find('Button').at(0).simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should disable the button if the url is invalid', () => {
  const node = shallow(<InsertLinkModal editor={editor} />);

  expect(node.find('Button').at(1).prop('disabled')).toBe(true);

  node
    .find('TextInput')
    .at(0)
    .simulate('change', {target: {value: 'http:/'}});

  node
    .find('TextInput')
    .at(1)
    .simulate('change', {target: {value: 'some link'}});

  expect(node.find('Button').at(1).prop('disabled')).toBe(true);

  node
    .find('TextInput')
    .at(0)
    .simulate('change', {target: {value: 'http://example.com'}});

  expect(node.find('Button').at(1).prop('disabled')).toBe(false);
});

it('should dispatch insert link command on report add button', () => {
  const spy = jest.fn();
  const node = shallow(<InsertLinkModal editor={editor} onClose={spy} />);

  node
    .find('TextInput')
    .at(0)
    .simulate('change', {target: {value: 'http://example.com'}});

  node
    .find('TextInput')
    .at(1)
    .simulate('change', {target: {value: 'some link'}});

  node.find('Button').at(1).simulate('click');

  expect(editor.dispatchCommand).toHaveBeenCalledWith(undefined, {
    altText: 'some link',
    url: 'http://example.com',
  });
  expect(spy).toHaveBeenCalled();
});

it('should dispatch insert link command with alt text defaulted to url when not provided', () => {
  const spy = jest.fn();
  const node = shallow(<InsertLinkModal editor={editor} onClose={spy} />);

  node
    .find('TextInput')
    .at(0)
    .simulate('change', {target: {value: 'http://example.com'}});

  node.find('Button').at(1).simulate('click');

  expect(editor.dispatchCommand).toHaveBeenCalledWith(undefined, {
    altText: 'http://example.com',
    url: 'http://example.com',
  });
  expect(spy).toHaveBeenCalled();
});
