/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {DefinitionSelection, BPMNDiagram, Modal} from 'components';
import {loadProcessDefinitionXml} from 'services';
import {track} from 'tracking';

import TemplateModal from './TemplateModal';

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    loadProcessDefinitionXml: jest.fn().mockReturnValue('processXML'),
  };
});

jest.mock('tracking', () => ({track: jest.fn()}));

jest.mock('hooks', () => ({
  useErrorHandling: () => ({
    mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  }),
}));

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
  entity: 'report',
  templateGroups: [
    {
      name: 'blankGroup',
      templates: [{name: 'blank', disableDescription: true}],
    },
    {
      name: 'templatesGroup',
      templates: [
        {
          name: 'locateBottlenecsOnAHitmap',
          disabled: (selectedDefinitions) => selectedDefinitions.length !== 1,
          img: <img alt="" />,
          config: {view: {entity: 'flowNode', properties: ['frequency']}},
        },
        {
          name: 'analyzeOrExportRawDataFromATable',
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
  expect(node.find('.templateContainer').find('Button').length).toBeGreaterThan(1);
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

  node.find('.templateContainer').find('Button').at(1).simulate('click');

  const confirmButton = node.find(Modal.Footer).find('Button').at(1);

  expect(confirmButton.prop('disabled')).toBe(false);
  expect(confirmButton.prop('to')).toMatchSnapshot();
  confirmButton.simulate('click');
  expect(track).toHaveBeenCalledWith('useReportTemplate', {
    templateName: 'Locate bottlenecks on a heatmap',
  });
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
    description: 'Locate duration or count bottlenecks visualized as heatmap for flow nodes.',
    name: 'Locate bottlenecks on a heatmap',
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

it('should show templates with descriptions', () => {
  const node = shallow(
    <TemplateModal
      {...props}
      entity="dashboard"
      templateGroups={[
        {
          name: 'singleProcessGroup',
          templates: [{name: 'portfolioPerformance'}],
        },
        {
          name: 'multiProcessGroup',
          templates: [{name: 'operationsMonitoring'}],
        },
      ]}
    />
  );

  expect(node.find('.templateContainer').find('Button')).toHaveClassName('hasDescription');
  expect(node.find('.description')).toExist();
});

it('should update the selected template if it is disabled for the selected definitions', async () => {
  const testDef = {key: 'def', versions: ['1'], tenantIds: [null]};
  const node = shallow(<TemplateModal {...props} />);

  expect(node.find('.active')).not.toExist();

  node.find(DefinitionSelection).simulate('change', [testDef]);
  runAllEffects();
  await flushPromises();

  expect(node.find('.active')).toIncludeText('heatmap');

  node.find(DefinitionSelection).simulate('change', [testDef, testDef]);
  runAllEffects();
  await flushPromises();

  expect(node.find('.active')).not.toIncludeText('Heatmap');
  expect(node.find('.active')).toIncludeText('Analyze or export raw data from a table');
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
  expect(track).toHaveBeenCalledWith('useReportTemplate', {
    templateName: 'Locate bottlenecks on a heatmap',
  });
  expect(node.find('.confirm')).not.toBeDisabled();
});

it('should override the track event name if trackingEventName is passed', async () => {
  const testDef = {key: 'def', versions: ['1'], tenantIds: [null]};
  const node = shallow(
    <TemplateModal
      {...props}
      initialDefinitions={[testDef]}
      trackingEventName={'useInstantPreviewDashboardTemplate'}
    />
  );

  expect(node.find('.confirm')).toBeDisabled();

  runAllEffects();
  await flushPromises();

  node.find('.confirm').simulate('click');

  expect(track).toHaveBeenCalledWith('useInstantPreviewDashboardTemplate', {
    templateName: 'Locate bottlenecks on a heatmap',
  });
});
