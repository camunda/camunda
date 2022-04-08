/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Deleter} from 'components';

import {removeProcess, loadProcess, cancelPublish} from './service';
import PublishModal from './PublishModal';

import ProcessViewWithErrorHandling from './ProcessView';

const ProcessView = ProcessViewWithErrorHandling.WrappedComponent;

jest.mock('./service', () => ({
  removeProcess: jest.fn(),
  loadProcess: jest.fn().mockReturnValue({
    id: 'processId',
    name: 'Process Name',
    xml: 'Process XML',
    mappings: {},
    state: 'mapped',
    lastModified: '2020-11-11T11:11:11.111+0200',
    lastModifier: 'john',
  }),
  cancelPublish: jest.fn(),
}));

const props = {
  id: 'processId',
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  onDelete: jest.fn(),
};

it('should match snapshot', () => {
  const node = shallow(<ProcessView {...props} />);

  expect(node).toMatchSnapshot();
});

it('should load process by id', () => {
  shallow(<ProcessView {...props} />);

  expect(loadProcess).toHaveBeenCalledWith('processId');
});

it('should pass entity to Deleter', () => {
  const node = shallow(<ProcessView {...props} />);

  node.find('.delete-button').simulate('click');

  expect(node.find(Deleter).prop('entity')).toEqual({id: 'processId', name: 'Process Name'});
});

it('should call the onDelete callback', () => {
  const node = shallow(<ProcessView {...props} />);

  node.find(Deleter).prop('onDelete')();

  expect(props.onDelete).toHaveBeenCalled();
});

it('should allow deletion of a process', () => {
  const node = shallow(<ProcessView {...props} />);

  node.find(Deleter).prop('deleteEntity')({id: '1'});

  expect(removeProcess).toHaveBeenCalledWith('1');
});

it('should pass a process id to the PublishModal', () => {
  const node = shallow(<ProcessView {...props} />);

  node.find('.publish-button').simulate('click');

  expect(node.find(PublishModal).prop('id')).toBe('processId');
  expect(node.find(PublishModal).prop('republish')).toBe(false);
});

it('should correctly set the republish prop on the PublishModal', () => {
  loadProcess.mockReturnValueOnce({
    id: 'processId',
    name: 'Process Name',
    xml: 'Process XML',
    mappings: {},
    state: 'unpublished_changes',
  });
  const node = shallow(<ProcessView {...props} />);

  node.find('.publish-button').simulate('click');

  expect(node.find(PublishModal).prop('republish')).toBe(true);
});

it('should allow cancel of an ongoing publish', () => {
  loadProcess.mockReturnValueOnce({
    id: 'processId',
    name: 'Process Name',
    xml: 'Process XML',
    mappings: {},
    state: 'publish_pending',
    publishingProgress: 14,
  });

  const node = shallow(<ProcessView {...props} />);

  node.find('.cancel-button').simulate('click');

  expect(cancelPublish).toHaveBeenCalledWith('processId');
});
