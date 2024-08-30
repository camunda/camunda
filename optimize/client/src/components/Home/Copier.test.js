/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {copyEntity} from 'services';

import CopierWithErrorHandling from './Copier';
import CopyModal from './modals/CopyModal';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  copyEntity: jest.fn(),
}));

const Copier = CopierWithErrorHandling.WrappedComponent;

const props = {
  mightFail: (promise, cb) => cb(promise),
  onCopy: jest.fn(),
  entity: {id: 'entityId', entityType: 'report'},
};

it('should not render anything when no entity is set', () => {
  const node = shallow(<Copier {...props} entity={null} />);

  expect(node).toMatchSnapshot();
});

it('should show the copy modal when entity is set', () => {
  const node = shallow(<Copier {...props} />);

  expect(node).toMatchSnapshot();
});

it('should copy the entity', () => {
  const node = shallow(<Copier {...props} />);

  node.find(CopyModal).prop('onConfirm')('new Name', false, 'aCollection');

  expect(copyEntity).toHaveBeenCalledWith('report', 'entityId', 'new Name', 'aCollection');
});

it('should call the onCopy callback', () => {
  const spy = jest.fn();
  const node = shallow(<Copier {...props} onCopy={spy} />);

  node.find(CopyModal).prop('onConfirm')('new Name', false, 'aCollection');

  expect(spy).toHaveBeenCalledWith('new Name', false, 'aCollection');
});
