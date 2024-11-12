/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runLastEffect} from '__mocks__/react';
import {shallow} from 'enzyme';

import {BPMNDiagram} from 'components';
import {loadProcessDefinitionXml} from 'services';

import {DiagramModal} from './DiagramModal';

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    loadProcessDefinitionXml: jest.fn().mockReturnValue('process xml'),
  };
});

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  onClose: jest.fn(),
  open: true,
  definition: {
    key: 'test',
    name: 'Test Definition',
    versions: ['testVersion'],
    tenantIds: ['testTenant'],
  },
};

it('should display BPMNDiagram with the report xml', () => {
  const node = shallow(<DiagramModal {...props} />);
  runLastEffect();
  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('test', 'testVersion', 'testTenant');
  expect(node.find(BPMNDiagram).prop('xml')).toBe('process xml');
});
