/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {runLastEffect} from '__mocks__/react';
import {shallow} from 'enzyme';

import {BPMNDiagram, DMNDiagram} from 'components';
import {loadProcessDefinitionXml, loadDecisionDefinitionXml} from 'services';

import {DiagramModal} from './DiagramModal';

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    loadProcessDefinitionXml: jest.fn().mockReturnValue('process xml'),
    loadDecisionDefinitionXml: jest.fn().mockReturnValue('decision xml'),
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
  const node = shallow(<DiagramModal {...props} type="process" />);
  runLastEffect();
  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('test', 'testVersion', 'testTenant');
  expect(node.find(BPMNDiagram).prop('xml')).toBe('process xml');
});

it('should display DMNDiagram with the report xml', () => {
  const node = shallow(<DiagramModal {...props} type="decision" />);
  runLastEffect();
  expect(loadDecisionDefinitionXml).toHaveBeenCalledWith('test', 'testVersion', 'testTenant');
  expect(node.find(DMNDiagram).prop('xml')).toBe('decision xml');
});
