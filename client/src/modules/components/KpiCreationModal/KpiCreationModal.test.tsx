/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';
import {ComboBox} from '@carbon/react';

import {DefinitionSelection} from 'components';
import {createEntity, evaluateReport} from 'services';
import {NodeSelection} from 'filter';

import KpiCreationModal from './KpiCreationModal';
import {automationRate} from './templates';

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    createEntity: jest.fn().mockReturnValue('id'),
    evaluateReport: jest.fn().mockImplementation((report: any) => report),
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
    mightFail: jest.fn().mockImplementation(async (data, cb, err, finallyFunc) => {
      await cb(data);
      finallyFunc?.();
    }),
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
  expect(evaluateReport).toHaveBeenCalled();

  await node.prop('steps')[1].actions.primary.onClick();
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

it('should change the target on the created kpi report', async () => {
  const node = shallow(<KpiCreationModal {...props} />);

  const firstStep = node.prop('steps')[0];
  const stepNode = shallow(firstStep.content);

  stepNode.find(ComboBox).simulate('change', {selectedItem: automationRate()});
  stepNode
    .find(DefinitionSelection)
    .simulate('change', {key: 'testProcess', versions: ['1'], tenantIds: [null, 'engineering']});

  await node.prop('steps')[0].actions.primary.onClick();

  const secondStep = node.prop('steps')[1];
  const secondStepNode = shallow(secondStep.content);

  secondStepNode
    .find('TargetSelection')
    .simulate('change', {targetValue: {countProgress: {target: {$set: '300'}}}});

  await node.prop('steps')[1].actions.primary.onClick();
  const params = (createEntity as jest.Mock).mock.calls[0];
  expect(params[1].data.configuration.targetValue.countProgress.target).toBe('300');
});

it('should add a filter to the kpi report', async () => {
  const node = shallow(<KpiCreationModal {...props} />);

  const firstStep = node.prop('steps')[0];
  const stepNode = shallow(firstStep.content);

  stepNode.find(ComboBox).simulate('change', {selectedItem: automationRate()});
  stepNode
    .find(DefinitionSelection)
    .simulate('change', {key: 'testProcess', versions: ['1'], tenantIds: [null, 'engineering']});

  await node.prop('steps')[0].actions.primary.onClick();
  await flushPromises();
  (evaluateReport as jest.Mock).mockClear();

  let secondStep = node.prop('steps')[1];
  let secondStepNode = shallow(secondStep.content);

  secondStepNode.find('.filterTile Button').at(0).simulate('click');

  secondStep = node.prop('steps')[1];
  secondStepNode = shallow(secondStep.content);

  secondStepNode.find(NodeSelection).prop('addFilter')({
    type: 'executedFlowNodes',
    data: {},
    appliedTo: [],
  });
  const evaluatedReport = (evaluateReport as jest.Mock).mock.calls[0][0];
  expect(evaluatedReport.data.filter.length).toBe(1);

  await node.prop('steps')[1].actions.primary.onClick();
  const params = (createEntity as jest.Mock).mock.calls[0];
  expect(params[1].data.filter[0].type).toBe('executedFlowNodes');
  expect(params[1].data.filter[0].filterLevel).toBe('view');
});
