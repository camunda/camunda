/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';
import {LexicalEditor} from 'lexical';
import {InsertImageModal} from './ImagesPlugin';

const editor = {
  dispatchCommand: jest.fn(),
} as unknown as LexicalEditor;

it('should display the link modal', () => {
  const node = shallow(<InsertImageModal editor={editor} />);

  expect(node).toExist();
});

it('should call the onClose when modal is closed', () => {
  const spy = jest.fn();
  const node = shallow(<InsertImageModal editor={editor} onClose={spy} />);

  node.find('Modal').prop<() => void>('onClose')();

  expect(spy).toHaveBeenCalled();

  node.find('ForwardRef(Button)').at(0).simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should disable the button if the url is invalid', () => {
  const node = shallow(<InsertImageModal editor={editor} />);

  expect(node.find('ForwardRef(Button)').at(1).prop('disabled')).toBe(true);

  node
    .find('ForwardRef(Input)')
    .at(0)
    .simulate('change', {target: {value: 'http:/'}});

  node
    .find('ForwardRef(Input)')
    .at(1)
    .simulate('change', {target: {value: 'some link'}});

  expect(node.find('ForwardRef(Button)').at(1).prop('disabled')).toBe(true);

  node
    .find('ForwardRef(Input)')
    .at(0)
    .simulate('change', {target: {value: 'http://example.com'}});

  expect(node.find('ForwardRef(Button)').at(1).prop('disabled')).toBe(false);
});

it('should dispatch insert link command on report add button', () => {
  const spy = jest.fn();
  const node = shallow(<InsertImageModal editor={editor} onClose={spy} />);

  node
    .find('ForwardRef(Input)')
    .at(0)
    .simulate('change', {target: {value: 'http://example.com'}});

  node
    .find('ForwardRef(Input)')
    .at(1)
    .simulate('change', {target: {value: 'some link'}});

  node.find('ForwardRef(Button)').at(1).simulate('click');

  expect(editor.dispatchCommand).toHaveBeenCalledWith(undefined, {
    altText: 'some link',
    src: 'http://example.com',
  });
  expect(spy).toHaveBeenCalled();
});

it('should dispatch insert image command with alt text defaulted to url when not provided', () => {
  const spy = jest.fn();
  const node = shallow(<InsertImageModal editor={editor} onClose={spy} />);

  node
    .find('ForwardRef(Input)')
    .at(0)
    .simulate('change', {target: {value: 'http://example.com'}});

  node.find('ForwardRef(Button)').at(1).simulate('click');

  expect(editor.dispatchCommand).toHaveBeenCalledWith(undefined, {
    altText: 'http://example.com',
    src: 'http://example.com',
  });
  expect(spy).toHaveBeenCalled();
});
