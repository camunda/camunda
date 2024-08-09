/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

it('should set aria label from modal header title when no label', () => {
  const node = shallow(
    <Modal open>
      <Modal.Header title="Title" />
    </Modal>
  );

  expect(node.find(ComposedModal).prop('aria-label')).toEqual('Title');
});

it('should set aria label from modal header label', () => {
  const node = shallow(
    <Modal open>
      <Modal.Header title="Title" label="Label" />
    </Modal>
  );

  expect(node.find(ComposedModal).prop('aria-label')).toEqual('Label');
});
