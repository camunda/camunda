/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {DefinitionSelection, Button, BPMNDiagram, LabeledInput} from 'components';
import {loadProcessDefinitionXml} from 'services';

import {ReportTemplateModal} from './ReportTemplateModal';

jest.mock('react', () => {
  const outstandingEffects = [];
  return {
    ...jest.requireActual('react'),
    useEffect: (fn) => outstandingEffects.push(fn),
    runLastEffect: () => {
      if (outstandingEffects.length) {
        outstandingEffects.pop()();
      }
    },
  };
});
jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    loadProcessDefinitionXml: jest.fn().mockReturnValue('processXML'),
  };
});

const props = {
  onClose: jest.fn(),
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

beforeEach(() => {
  loadProcessDefinitionXml.mockClear();
});

it('should show a definition selection, and a list of templates', () => {
  const node = shallow(<ReportTemplateModal {...props} />);

  expect(node.find(DefinitionSelection)).toExist();
  expect(node.find('.templateContainer').find(Button).length).toBeGreaterThan(1);
});

it('should fetch and show the bpmn diagram when a definition is selected', () => {
  const node = shallow(<ReportTemplateModal {...props} />);

  node
    .find(DefinitionSelection)
    .simulate('change', {key: 'processDefinition', versions: ['1'], tenantIds: [null]});

  runLastEffect();

  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('processDefinition', '1', null);
  expect(node.find(BPMNDiagram)).toExist();
  expect(node.find(BPMNDiagram).prop('xml')).toBe('processXML');
});

it('should include the selected parameters in the link state when creating a report', () => {
  const node = shallow(<ReportTemplateModal {...props} />);

  node.find(DefinitionSelection).simulate('change', {
    key: 'processDefinition',
    versions: ['1'],
    tenantIds: [null],
    name: 'Process Definition Name',
  });

  runLastEffect();

  node.find(LabeledInput).simulate('change', {target: {value: 'Template Report Name'}});
  node.find('.templateContainer').find(Button).at(1).simulate('click');

  expect(node.find('.Button.primary').prop('disabled')).toBe(false);
  expect(node.find('.Button.primary').prop('to')).toMatchSnapshot();
});
