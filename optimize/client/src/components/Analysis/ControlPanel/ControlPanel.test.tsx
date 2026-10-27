/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {ControlPanel} from './ControlPanel';
import {runAllEffects} from '__mocks__/react';

import {loadVariables} from 'services';
import {Layer} from '@carbon/react';

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    loadVariables: jest.fn().mockReturnValue([{name: 'variable1', type: 'String'}]),
  };
});

jest.mock('hooks', () => ({
  useErrorHandling: () => ({
    mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  }),
}));

const data = {
  processDefinitionKey: 'aKey',
  processDefinitionVersions: ['aVersion'],
  tenantIds: [],
  filters: [],
  onChange: jest.fn(),
};

it('should load the variable names and hand them to the filter if process definition changes', async () => {
  const node = shallow(
    <ControlPanel
      {...data}
      processDefinitionKey="fooKey"
      processDefinitionVersions={['fooVersion']}
    />
  );

  runAllEffects();
  await flushPromises();

  expect(loadVariables).toHaveBeenCalled();
  expect(node.find('Filter').prop('variables')).toEqual(
    loadVariables({processesToQuery: [], filter: []})
  );
});

it('should render the children properly', () => {
  const node = shallow(
    <ControlPanel {...data}>
      <p>child1</p>
      <p>child2</p>
    </ControlPanel>
  );

  expect(node.find(Layer).find('li p').at(0)).toHaveText('child1');
  expect(node.find(Layer).find('li p').at(1)).toHaveText('child2');
});
