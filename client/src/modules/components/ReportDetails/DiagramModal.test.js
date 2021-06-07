/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
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
  close: jest.fn(),
};

it('Should display BPMNDiagram with the report xml', () => {
  const data = {
    definitions: [
      {
        key: 'test',
        versions: ['testVersion'],
        tenantIds: ['testTenant'],
      },
    ],
  };
  const node = shallow(<DiagramModal {...props} report={{reportType: 'process', data}} />);
  runLastEffect();
  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('test', 'testVersion', 'testTenant');
  expect(node.find(BPMNDiagram).prop('xml')).toBe('process xml');
});

it('Should display DMNDiagram with the report xml', () => {
  const data = {
    definitions: [
      {
        key: 'test',
        versions: ['testVersion'],
        tenantIds: ['testTenant'],
      },
    ],
  };
  const node = shallow(<DiagramModal {...props} report={{reportType: 'decision', data}} />);
  runLastEffect();
  expect(loadDecisionDefinitionXml).toHaveBeenCalledWith('test', 'testVersion', 'testTenant');
  expect(node.find(DMNDiagram).prop('xml')).toBe('decision xml');
});
