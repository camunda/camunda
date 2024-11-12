/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import BranchControlPanel from './BranchControlPanel';

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    loadVariables: jest.fn().mockReturnValue([{name: 'variable1', type: 'String'}]),
  };
});

const data = {
  processDefinitionKey: 'aKey',
  processDefinitionVersions: ['aVersion'],
  tenantIds: [],
  filter: null,
  xml: 'aFooXml',
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

const emptyData = {
  processDefinitionKey: '',
  processDefinitionVersions: [],
  tenantIds: [],
  filter: null,
  xml: null,
};

const spy = jest.fn();

it('should contain a gateway and end Event field', () => {
  const node = shallow(<BranchControlPanel {...data} onChange={spy} />);

  expect(node.find('[name="gateway"]')).toExist();
  expect(node.find('[name="endEvent"]')).toExist();
});

it('should show a please select message if an entity is not selected', () => {
  const node = shallow(<BranchControlPanel {...data} onChange={spy} />);

  expect(node.find('SelectionPreview').at(0).dive()).toIncludeText('Select gateway');
  expect(node.find('SelectionPreview').at(1).dive()).toIncludeText('Select end event');
});

it('should show the element name if an element is selected', () => {
  const node = shallow(
    <BranchControlPanel
      {...data}
      onChange={spy}
      gateway={{
        name: 'I am a Gateway',
        id: 'gatewayId',
      }}
    />
  );

  expect(node.find('SelectionPreview').at(0).dive()).toIncludeText('I am a Gateway');
  expect(node.find('SelectionPreview').at(0).dive()).not.toIncludeText('gatewayId');
});

it('should show the element id if an element has no name', () => {
  const node = shallow(
    <BranchControlPanel
      {...data}
      onChange={spy}
      gateway={{
        name: undefined,
        id: 'gatewayId',
      }}
    />
  );

  expect(node.find('SelectionPreview').at(0).dive()).toIncludeText('gatewayId');
});

it('should disable gateway and EndEvent elements if no ProcDef selected', async () => {
  const node = await shallow(<BranchControlPanel hoveredControl="gateway" {...emptyData} />);

  expect(node.find('SelectionPreview').at(0)).toBeDisabled();
  expect(node.find('SelectionPreview').at(1)).toBeDisabled();

  expect(node.find('SelectionPreview').at(1)).not.toHaveClassName(
    'BranchControlPanel__config--hover'
  );
});

it('should display a sentence to describe what the user can do on this page', () => {
  const node = shallow(<BranchControlPanel {...emptyData} />);

  expect(node.find('ControlPanel').dive().text()).toMatch(
    /For(.*?)analyze how the branches of(.*?)affect the probability that an instance reached/
  );
});
