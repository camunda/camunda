/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {DefinitionSelection, Button, BPMNDiagram, CarbonModal as Modal} from 'components';
import {loadProcessDefinitionXml} from 'services';

import {TemplateModal} from './TemplateModal';

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    loadProcessDefinitionXml: jest.fn().mockReturnValue('processXML'),
  };
});

beforeEach(() => {
  Object.defineProperty(global, 'ResizeObserver', {
    writable: true,
    value: jest.fn().mockImplementation(() => ({
      observe: jest.fn(() => 'Mocking works'),
      disconnect: jest.fn(),
    })),
  });
});

const props = {
  onClose: jest.fn(),
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  entity: 'report',
  templateGroups: [
    {
      name: 'blankGroup',
      templates: [{name: 'blank'}],
    },
    {
      name: 'templatesGroup',
      templates: [
        {
          name: 'heatmap',
          disabled: (selectedDefinitions) => selectedDefinitions.length !== 1,
          img: <img alt="" />,
          config: {view: {entity: 'flowNode', properties: ['frequency']}},
        },
        {
          name: 'chart',
          config: {},
        },
      ],
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

  runAllEffects();
  await flushPromises();
  runAllEffects();

  expect(node.find(BPMNDiagram).length).toBe(1);
  expect(node.find(BPMNDiagram).at(0).prop('xml')).toBe('processXML');
  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('def1', '1', null);
  loadProcessDefinitionXml.mockClear();

  node.find(DefinitionSelection).simulate('change', [def1, def2]);

  runAllEffects();
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
      identifier: 'definition',
    },
  ]);

  runAllEffects();
  await flushPromises();

  node.find('.templateContainer').find(Button).at(1).simulate('click');

  const confirmButton = node.find(Modal.Footer).find('Button').at(1);

  expect(confirmButton.prop('disabled')).toBe(false);
  expect(confirmButton.prop('to')).toMatchSnapshot();
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
    },
  ]);

  runAllEffects();
  await flushPromises();

  expect(spy).toHaveBeenCalledWith({
    name: 'Heatmap: Flownode count',
    definitions: [
      {
        key: 'processDefinition',
        name: 'Process Definition Name',
        displayName: 'Process Definition Name',
        tenantIds: [null],
        versions: ['1'],
      },
    ],
    template: props.templateGroups[1].templates[0].config,
    xml: 'processXML',
  });

  const confirmButton = node.find(Modal.Footer).find('Button').at(1);
  expect(confirmButton.prop('to').state).toEqual({data: 'stateData'});
});

it('should show templates with subTitles', () => {
  const node = shallow(
    <TemplateModal
      {...props}
      entity="dashboard"
      templateGroups={[
        {name: 'singleProcessGroup', templates: [{name: 'processPerformance', hasSubtitle: true}]},
        {
          name: 'multiProcessGroup',
          templates: [{name: 'humanPerformance', hasSubtitle: true}],
        },
      ]}
    />
  );

  expect(node.find('.templateContainer').find(Button)).toHaveClassName('hasSubtitle');
  expect(node.find('.subTitle')).toExist();
});

it('should update the selected template if it is disabled for the selected definitions', async () => {
  const testDef = {key: 'def', versions: ['1'], tenantIds: [null]};
  const node = shallow(<TemplateModal {...props} />);

  expect(node.find('.active')).not.toExist();

  node.find(DefinitionSelection).simulate('change', [testDef]);
  runAllEffects();
  await flushPromises();

  expect(node.find('.active')).toIncludeText('Heatmap');

  node.find(DefinitionSelection).simulate('change', [testDef, testDef]);
  runAllEffects();
  await flushPromises();

  expect(node.find('.active')).not.toIncludeText('Heatmap');
  expect(node.find('.active')).toIncludeText('Bar Chart');
});

it('should invoke the onConfirm when when clicking the create button', async () => {
  const testDef = {key: 'def', versions: ['1'], tenantIds: [null]};
  const spy = jest.fn();
  const node = shallow(<TemplateModal {...props} onConfirm={spy} />);

  expect(node.find('.confirm')).toBeDisabled();

  node.find(DefinitionSelection).simulate('change', [testDef]);
  runAllEffects();
  await flushPromises();

  node.find('.confirm').simulate('click');

  expect(spy).toHaveBeenCalled();
  expect(node.find('.confirm')).not.toBeDisabled();
});
