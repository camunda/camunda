/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {extractDefinitionName, getFlowNodeNames} from 'services';

import AnalysisControlPanel from './AnalysisControlPanel';

jest.mock('../Reports', () => {
  return {
    Filter: () => 'Filter'
  };
});

jest.mock('components', () => {
  return {
    ActionItem: props => (
      <button id="actionItem" {...props}>
        {props.children}
      </button>
    ),
    Popover: ({title, children}) => (
      <div>
        {title} {children}
      </div>
    ),
    Labeled: props => (
      <div>
        <label id={props.id}>{props.label}</label>
        {props.children}
      </div>
    ),
    DefinitionSelection: props => <div>DefinitionSelection</div>
  };
});

jest.mock('services', () => {
  return {
    extractDefinitionName: jest.fn(),
    getFlowNodeNames: jest.fn().mockReturnValue({
      a: 'foo',
      b: 'bar'
    })
  };
});

const flushPromises = () => new Promise(resolve => setImmediate(resolve));

const data = {
  processDefinitionKey: 'aKey',
  processDefinitionVersion: 'aVersion',
  filter: null,
  xml: 'aFooXml'
};

const emptyData = {
  processDefinitionKey: '',
  processDefinitionVersion: '',
  filter: null,
  xml: null
};

extractDefinitionName.mockReturnValue('foo');
const spy = jest.fn();

it('should contain a gateway and end Event field', () => {
  const node = mount(<AnalysisControlPanel {...data} onChange={spy} />);

  expect(node.find('[name="AnalysisControlPanel__gateway"]')).toBePresent();
  expect(node.find('[name="AnalysisControlPanel__endEvent"]')).toBePresent();
});

it('should show a please select message if an entity is not selected', () => {
  const node = mount(<AnalysisControlPanel {...data} onChange={spy} />);

  expect(node).toIncludeText('Please Select End Event');
  expect(node).toIncludeText('Please Select Gateway');
});

it('should show the element name if an element is selected', () => {
  const node = mount(
    <AnalysisControlPanel
      {...data}
      onChange={spy}
      gateway={{
        name: 'I am a Gateway',
        id: 'gatewayId'
      }}
    />
  );

  expect(node).toIncludeText('I am a Gateway');
  expect(node).not.toIncludeText('gatewayId');
});

it('should show the element id if an element has no name', () => {
  const node = mount(
    <AnalysisControlPanel
      {...data}
      onChange={spy}
      gateway={{
        name: undefined,
        id: 'gatewayId'
      }}
    />
  );

  expect(node).toIncludeText('gatewayId');
});

it('should show initially show process definition name if xml is available', async () => {
  extractDefinitionName.mockReturnValue('aName');

  const node = await mount(<AnalysisControlPanel {...data} />);

  expect(node.find('.AnalysisControlPanel__popover')).toIncludeText('aName');
});

it('should change process definition name if process definition xml is updated', async () => {
  const node = await mount(<AnalysisControlPanel {...data} />);

  extractDefinitionName.mockReturnValue('aName');
  await node.setProps({xml: 'barXml'});

  expect(node.find('.AnalysisControlPanel__popover')).toIncludeText('aName');
});

it('should disable gateway and EndEvent elements if no ProcDef selected', async () => {
  const node = await mount(<AnalysisControlPanel hoveredControl="gateway" {...emptyData} />);

  expect(node.find('#actionItem').first()).toBeDisabled();
  expect(node.find('#actionItem').at(1)).toBeDisabled();

  expect(node.find('.AnalysisControlPanel__config').at(1)).not.toHaveClassName(
    'AnalysisControlPanel__config--hover'
  );
});

it('should pass the xml to the Filter component', async () => {
  const node = await mount(<AnalysisControlPanel {...data} />);
  const filter = node.find('Filter');
  expect(filter.find('[xml="aFooXml"]')).toBePresent();
});

it('should load the flownode names and hand them to the filter', async () => {
  const node = mount(<AnalysisControlPanel {...data} />);

  await flushPromises();
  node.update();

  expect(getFlowNodeNames).toHaveBeenCalled();
  expect(node.find('Filter').prop('flowNodeNames')).toEqual(getFlowNodeNames());
});

it('should display a sentence to describe what the user can do on this page', () => {
  const node = mount(<AnalysisControlPanel {...emptyData} />);

  expect(node).toMatchSnapshot();
});
