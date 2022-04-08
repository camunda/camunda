/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import CopyModal from './CopyModal';

const props = {
  entity: {name: 'Test Dashboard', entityType: 'dashboard', data: {subEntityCounts: {report: 2}}},
  collection: 'aCollectionId',
  onConfirm: jest.fn(),
  onClose: jest.fn(),
};

it('should match snapshot', () => {
  const node = shallow(<CopyModal {...props} />);

  expect(node).toMatchSnapshot();
});

it('should hide option to move the copy for collection entities', () => {
  const node = shallow(
    <CopyModal {...props} entity={{name: 'collection', entityType: 'collection'}} />
  );

  expect(node).toMatchSnapshot();
});

it('should call the onConfirm action', () => {
  const node = shallow(
    <CopyModal {...props} jumpToEntity entity={{name: 'collection', entityType: 'collection'}} />
  );

  node.find('.confirm').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith('collection (copy)', true);
});

it('should hide the jump checkbox if jumpToEntity property is not added', () => {
  const node = shallow(<CopyModal {...props} />);

  expect(node.find('LabeledInput[type="checkbox"]')).not.toExist();

  node.find('.confirm').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith('Test Dashboard (copy)', false, false);
});

it('should call the onConfirm with false redirect parameter if entity is not a collection', () => {
  const node = shallow(<CopyModal {...props} jumpToEntity />);

  node.find('.confirm').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith('Test Dashboard (copy)', false, false);
});
