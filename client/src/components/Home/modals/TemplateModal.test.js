/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {DefinitionSelection, Button, BPMNDiagram} from 'components';
import {loadProcessDefinitionXml} from 'services';

import {TemplateModal} from './TemplateModal';

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    loadProcessDefinitionXml: jest.fn().mockReturnValue('processXML'),
  };
});

const props = {
  onClose: jest.fn(),
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  entity: 'report',
  templates: [
    {name: 'blank'},
    {
      name: 'heatmap',
      img: <img alt="" />,
      config: {view: {entity: 'flowNode', properties: ['frequency']}},
    },
  ],
};

beforeEach(() => {
  loadProcessDefinitionXml.mockClear();
});

it('should show a definition selection, and a list of templates', () => {
  const node = shallow(<TemplateModal {...props} />);

  expect(node.find(DefinitionSelection)).toExist();
  expect(node.find('.templateContainer').find(Button).length).toBeGreaterThan(1);
});

it('should fetch and show the bpmn diagram when a definition is selected', () => {
  const node = shallow(<TemplateModal {...props} />);

  node
    .find(DefinitionSelection)
    .simulate('change', {key: 'processDefinition', versions: ['1'], tenantIds: [null]});

  runLastEffect();

  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('processDefinition', '1', null);
  expect(node.find(BPMNDiagram)).toExist();
  expect(node.find(BPMNDiagram).prop('xml')).toBe('processXML');
});

it('should include the selected parameters in the link state when creating a report', () => {
  const node = shallow(<TemplateModal {...props} />);

  node.find(DefinitionSelection).simulate('change', {
    key: 'processDefinition',
    versions: ['1'],
    tenantIds: [null],
    name: 'Process Definition Name',
    identifier: 'definition',
  });

  runLastEffect();

  node.find('.templateContainer').find(Button).at(1).simulate('click');

  expect(node.find('.Button.primary').prop('disabled')).toBe(false);
  expect(node.find('.Button.primary').prop('to')).toMatchSnapshot();
});

it('should call the templateToState prop to determine link state', () => {
  const spy = jest.fn().mockReturnValue({data: 'stateData'});
  const node = shallow(<TemplateModal {...props} templateToState={spy} />);

  node.find(DefinitionSelection).simulate('change', {
    key: 'processDefinition',
    versions: ['1'],
    tenantIds: [null],
    name: 'Process Definition Name',
  });

  runLastEffect();

  expect(spy).toHaveBeenCalledWith({
    name: 'New Report',
    definitions: [
      {
        key: 'processDefinition',
        name: 'Process Definition Name',
        displayName: 'Process Definition Name',
        tenantIds: [null],
        versions: ['1'],
      },
    ],
    template: undefined,
    xml: 'processXML',
  });
  expect(node.find('.confirm.Button').prop('to').state).toEqual({data: 'stateData'});
});

it('should show templates with subTitles', () => {
  const node = shallow(
    <TemplateModal
      {...props}
      entity="dashboard"
      templates={[{name: 'processPerformance', hasSubtitle: true}]}
    />
  );

  expect(node.find('.templateContainer').find(Button)).toHaveClassName('hasSubtitle');
  expect(node.find('.subTitle')).toExist();
});
