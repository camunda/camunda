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

it('should fetch and show the bpmn diagrams when definitions are selected', async () => {
  const node = shallow(<TemplateModal {...props} />);

  const def1 = {key: 'def1', versions: ['1'], tenantIds: [null]};
  const def2 = {key: 'def2', versions: ['2'], tenantIds: [null]};

  node.find(DefinitionSelection).simulate('change', [def1]);

  runLastEffect();
  await flushPromises();

  expect(node.find(BPMNDiagram).length).toBe(1);
  expect(node.find(BPMNDiagram).at(0).prop('xml')).toBe('processXML');
  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('def1', '1', null);
  loadProcessDefinitionXml.mockClear();

  node.find(DefinitionSelection).simulate('change', [def1, def2]);

  runLastEffect();
  await flushPromises();

  expect(node.find(BPMNDiagram).length).toBe(2);
  expect(loadProcessDefinitionXml).not.toHaveBeenCalledWith('def1', '1', null);
  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('def2', '2', null);
});

it('should include the selected parameters in the link state when creating a report', async () => {
  const node = shallow(<TemplateModal {...props} />);

  node.find(DefinitionSelection).simulate('change', [
    {
      key: 'processDefinition',
      versions: ['1'],
      tenantIds: [null],
      name: 'Process Definition Name',
      displayName: 'Process Definition Name',
      identifier: 'definition',
    },
  ]);

  runLastEffect();
  await flushPromises();

  node.find('.templateContainer').find(Button).at(1).simulate('click');

  expect(node.find('.Button.primary').prop('disabled')).toBe(false);
  expect(node.find('.Button.primary').prop('to')).toMatchSnapshot();
});

it('should call the templateToState prop to determine link state', async () => {
  const spy = jest.fn().mockReturnValue({data: 'stateData'});
  const node = shallow(<TemplateModal {...props} templateToState={spy} />);

  node.find(DefinitionSelection).simulate('change', [
    {
      key: 'processDefinition',
      versions: ['1'],
      tenantIds: [null],
      name: 'Process Definition Name',
      displayName: 'Process Definition Name',
    },
  ]);

  runLastEffect();
  await flushPromises();

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
