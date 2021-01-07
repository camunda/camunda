/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {loadRawData} from 'services';
import {InstancesButton} from './InstancesButton';
import {Button} from 'components';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  formatters: {
    formatFileName: (name) => name,
  },
  loadRawData: jest.fn(),
}));

const props = {
  id: 'flowNodeId',
  name: 'flowNodeName',
  value: 1250,
  config: {
    processDefinitionKey: 'defKey',
    processDefinitionVersions: ['1'],
    tenantIds: ['sales'],
  },
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('invoke loadRawData on button Click', () => {
  const node = shallow(<InstancesButton {...props} />);

  window.URL.createObjectURL = jest.fn();
  node.find(Button).simulate('click');

  expect(loadRawData).toHaveBeenCalledWith({
    filter: [
      {
        data: {flowNodeId: {operator: '>', unit: 'millis', value: 1250}},
        type: 'flowNodeDuration',
        filterLevel: 'instance',
      },
    ],
    includedColumns: ['processInstanceId'],
    ...props.config,
  });
});
