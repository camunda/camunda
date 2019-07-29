/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {getFlowNodeNames} from 'services';

import AnalysisControlPanel from './AnalysisControlPanel';

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    getFlowNodeNames: jest.fn().mockReturnValue({
      a: 'foo',
      b: 'bar'
    })
  };
});

const flushPromises = () => new Promise(resolve => setImmediate(resolve));

const data = {
  processDefinitionKey: 'aKey',
  processDefinitionVersions: ['aVersion'],
  tenantIds: [],
  filter: null,
  xml: 'aFooXml'
};

const emptyData = {
  processDefinitionKey: '',
  processDefinitionVersions: [],
  tenantIds: [],
  filter: null,
  xml: null
};

const spy = jest.fn();

it('should contain a gateway and end Event field', () => {
  const node = shallow(<AnalysisControlPanel {...data} onChange={spy} />);

  expect(node.find('[name="gateway"]')).toExist();
  expect(node.find('[name="endEvent"]')).toExist();
});

it('should show a please select message if an entity is not selected', () => {
  const node = shallow(<AnalysisControlPanel {...data} onChange={spy} />);

  expect(
    node
      .find('ActionItem')
      .at(0)
      .dive()
  ).toIncludeText('Select Gateway');
  expect(
    node
      .find('ActionItem')
      .at(1)
      .dive()
  ).toIncludeText('Select End Event');
});

it('should show the element name if an element is selected', () => {
  const node = shallow(
    <AnalysisControlPanel
      {...data}
      onChange={spy}
      gateway={{
        name: 'I am a Gateway',
        id: 'gatewayId'
      }}
    />
  );

  expect(
    node
      .find('ActionItem')
      .at(0)
      .dive()
  ).toIncludeText('I am a Gateway');
  expect(
    node
      .find('ActionItem')
      .at(0)
      .dive()
  ).not.toIncludeText('gatewayId');
});

it('should show the element id if an element has no name', () => {
  const node = shallow(
    <AnalysisControlPanel
      {...data}
      onChange={spy}
      gateway={{
        name: undefined,
        id: 'gatewayId'
      }}
    />
  );

  expect(
    node
      .find('ActionItem')
      .at(0)
      .dive()
  ).toIncludeText('gatewayId');
});

it('should disable gateway and EndEvent elements if no ProcDef selected', async () => {
  const node = await shallow(<AnalysisControlPanel hoveredControl="gateway" {...emptyData} />);

  expect(node.find('ActionItem').at(0)).toBeDisabled();
  expect(node.find('ActionItem').at(1)).toBeDisabled();

  expect(node.find('ActionItem').at(1)).not.toHaveClassName('AnalysisControlPanel__config--hover');
});

it('should pass the xml to the Filter component', async () => {
  const node = await shallow(<AnalysisControlPanel {...data} />);
  const filter = node.find('Filter');
  expect(filter.find('[xml="aFooXml"]')).toExist();
});

it('should load the flownode names and hand them to the filter if process definition changes', async () => {
  const node = shallow(<AnalysisControlPanel {...data} />);
  node.setProps({
    processDefinitionKey: 'fooKey',
    processDefinitionVersions: ['fooVersion']
  });

  await flushPromises();
  node.update();

  expect(getFlowNodeNames).toHaveBeenCalled();
  expect(node.find('Filter').prop('flowNodeNames')).toEqual(getFlowNodeNames());
});

it('should display a sentence to describe what the user can do on this page', () => {
  const node = shallow(<AnalysisControlPanel {...emptyData} />);

  expect(node).toMatchSnapshot();
});
