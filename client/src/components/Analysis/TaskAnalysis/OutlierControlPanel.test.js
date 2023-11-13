/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {loadVariables} from 'services';

import OutlierControlPanel from './OutlierControlPanel';

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
  xml: 'aFooXml',
};

it('show display a definition selection and an info message', () => {
  const node = shallow(<OutlierControlPanel {...data} />);

  expect(node).toMatchSnapshot();
});

it('should load the variable names and hand them to the filter if process definition changes', async () => {
  const node = shallow(
    <OutlierControlPanel
      {...data}
      processDefinitionKey="fooKey"
      processDefinitionVersions={['fooVersion']}
    />
  );

  runAllEffects();
  await flushPromises();

  expect(loadVariables).toHaveBeenCalled();
  expect(node.find('Filter').prop('variables')).toEqual(loadVariables());
});
