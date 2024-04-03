/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
  ...jest.requireActual('hooks'),
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
  expect(node.find('Filter').prop('variables')).toEqual(loadVariables([]));
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
