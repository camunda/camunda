/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';
import {ComposedModal, ModalBody} from '@carbon/react';

import Modal from './Modal';

it('should render without throwing an error', () => {
  const node = shallow(<Modal />);
  expect(node.exists()).toBe(true);
});

it('should render children', () => {
  const node = shallow(
    <Modal open>
      <div>Child content</div>
    </Modal>
  );
  expect(node.find('div').text()).toEqual('Child content');
});

it('should call onClose when close button is clicked', () => {
  const spy = jest.fn();
  const node = shallow(<Modal open onClose={spy} />);

  node.find(ComposedModal).simulate('close');
  expect(spy).toHaveBeenCalled();
});

it('should call onClick callback when clicked', () => {
  const spy = jest.fn();
  const node = shallow(<Modal.Content onClick={spy}>Some Content</Modal.Content>);
  node.find(ModalBody).simulate('click', {stopPropagation: jest.fn()});
  expect(spy).toHaveBeenCalled();
});

it('should call onMouseDown callback when mouse down', () => {
  const spy = jest.fn();
  const node = shallow(<Modal.Content onMouseDown={spy}>Some Content</Modal.Content>);
  node.find(ModalBody).simulate('mousedown', {stopPropagation: jest.fn()});
  expect(spy).toHaveBeenCalled();
});

it('should stop propagation of events', () => {
  const spy = jest.fn();
  const node = shallow(<Modal.Content>Some Content</Modal.Content>);
  node.find(ModalBody).simulate('click', {
    stopPropagation: spy,
  });
  expect(spy).toHaveBeenCalled();
});
