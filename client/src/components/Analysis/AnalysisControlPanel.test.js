/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {getFlowNodeNames} from 'services';

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
  tenantIds: [],
  filter: null,
  xml: 'aFooXml'
};

const emptyData = {
  processDefinitionKey: '',
  processDefinitionVersion: '',
  tenantIds: [],
  filter: null,
  xml: null
};

const spy = jest.fn();

it('should contain a gateway and end Event field', () => {
  const node = mount(<AnalysisControlPanel {...data} onChange={spy} />);

  expect(node.find('[name="AnalysisControlPanel__gateway"]')).toExist();
  expect(node.find('[name="AnalysisControlPanel__endEvent"]')).toExist();
});

it('should show a please select message if an entity is not selected', () => {
  const node = mount(<AnalysisControlPanel {...data} onChange={spy} />);

  expect(node).toIncludeText('Select End Event');
  expect(node).toIncludeText('Select Gateway');
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
  expect(filter.find('[xml="aFooXml"]')).toExist();
});

it('should load the flownode names and hand them to the filter if process definition changes', async () => {
  const node = mount(<AnalysisControlPanel {...data} />);
  node.setProps({
    processDefinitionKey: 'fooKey',
    processDefinitionVersion: 'fooVersion'
  });

  await flushPromises();
  node.update();

  expect(getFlowNodeNames).toHaveBeenCalled();
  expect(node.find('Filter').prop('flowNodeNames')).toEqual(getFlowNodeNames());
});

it('should display a sentence to describe what the user can do on this page', () => {
  const node = mount(<AnalysisControlPanel {...emptyData} />);

  expect(node).toMatchSnapshot();
});
