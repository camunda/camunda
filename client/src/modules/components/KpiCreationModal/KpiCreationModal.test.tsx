/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';
import {ComboBox} from '@carbon/react';

import {DefinitionSelection} from 'components';
import {createEntity} from 'services';

import KpiCreationModal from './KpiCreationModal';
import {automationRate} from './templates';

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    createEntity: jest.fn().mockReturnValue('id'),
  };
});

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useHistory: () => ({push: jest.fn()}),
  useLocation: jest.fn().mockReturnValue({pathname: '/report/'}),
}));

jest.mock('hooks', () => ({
  ...jest.requireActual('hooks'),
  useErrorHandling: () => ({
    mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  }),
}));

const props = {
  onClose: jest.fn(),
};

beforeEach(() => {
  jest.clearAllMocks();
});

it('should invoke createEntity when creating a kpi', async () => {
  const node = shallow(<KpiCreationModal {...props} />);

  const firstStep = node.prop('steps')[0];
  const stepNode = shallow(firstStep.content);

  stepNode.find(ComboBox).simulate('change', {selectedItem: automationRate()});
  stepNode
    .find(DefinitionSelection)
    .simulate('change', {key: 'testProcess', versions: ['1'], tenantIds: [null, 'engineering']});

  await node.prop('steps')[0].actions.primary.onClick();
  const params = (createEntity as jest.Mock).mock.calls[0];
  expect(params[0]).toBe('report/process/single');
  expect(params[1].data.definitions).toEqual([
    {
      identifier: 'definition',
      key: 'testProcess',
      tenantIds: [null, 'engineering'],
      versions: ['1'],
    },
  ]);

  expect(params[1].name).toBe('Automation rate');
});
