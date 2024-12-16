/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runLastEffect} from '__mocks__/react';
import {shallow} from 'enzyme';
import {ComboBox} from '@carbon/react';

import {DefinitionSelection} from 'components';
import {createEntity, evaluateReport} from 'services';
import {NodeSelection} from 'filter';
import {track} from 'tracking';

import KpiCreationModal from './KpiCreationModal';
import {automationRate} from './templates';

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    createEntity: jest.fn().mockReturnValue('id'),
    evaluateReport: jest.fn().mockImplementation((report: unknown) => report),
  };
});

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useHistory: () => ({push: jest.fn()}),
  useLocation: jest.fn().mockReturnValue({pathname: '/report/'}),
}));

jest.mock('hooks', () => ({
  useErrorHandling: () => ({
    mightFail: jest.fn().mockImplementation(async (data, cb, _err, finallyFunc) => {
      await cb(data);
      finallyFunc?.();
    }),
  }),
}));

jest.mock('tracking', () => ({track: jest.fn()}));

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
  const {node, secondStepNode} = setupKpiModal();

  secondStepNode
    .find('TargetSelection')
    .simulate('change', {targetValue: {countProgress: {target: {$set: '300'}}}});

  await node.prop('steps')[1].actions.primary.onClick();
  const params = (createEntity as jest.Mock).mock.calls[0];
  expect(params[1].data.configuration.targetValue.countProgress.target).toBe('300');
});

it('should add filter', async () => {
  const {node} = setupKpiModal();
  const evaluatedReport = (evaluateReport as jest.Mock).mock.calls[0][0];
  expect(evaluatedReport.data.filter.length).toBe(1);

  await node.prop('steps')[1].actions.primary.onClick();
  const params = (createEntity as jest.Mock).mock.calls[0];
  expect(params[1].data.filter[0].type).toBe('executedFlowNodes');
  expect(params[1].data.filter[0].filterLevel).toBe('view');
});

it('should delete filter ', async () => {
  const {secondStepNode} = setupKpiModal();
  (evaluateReport as jest.Mock).mockClear();
  secondStepNode.find({iconDescription: 'Delete'}).simulate('click');

  const evaluatedReport = (evaluateReport as jest.Mock).mock.calls[0][0];
  expect(evaluatedReport.data.filter.length).toBe(0);
});

it('should edit filter ', async () => {
  const {node, secondStepNode} = setupKpiModal();
  (evaluateReport as jest.Mock).mockClear();
  secondStepNode.find({iconDescription: 'Edit'}).simulate('click');
  const nodeWithFilter = shallow(node.prop('steps')[1].content);

  expect(nodeWithFilter.find(NodeSelection)).toExist();
});

it('should invoke mixpanel events', async () => {
  shallow(<KpiCreationModal {...props} />);
  runLastEffect();

  expect(track).toHaveBeenCalledWith('openKpiWizzard');

  const {node} = setupKpiModal();
  await node.prop('steps')[1].actions.primary.onClick();

  expect(track).toHaveBeenCalledWith('createKPIReport', {template: automationRate().name});
});

function setupKpiModal() {
  const node = shallow(<KpiCreationModal {...props} />);

  // first step
  const firstStepNode = shallow(node.prop('steps')[0].content);
  firstStepNode.find(ComboBox).simulate('change', {selectedItem: automationRate()});
  firstStepNode
    .find(DefinitionSelection)
    .simulate('change', {key: 'testProcess', versions: ['1'], tenantIds: [null, 'engineering']});

  node.prop('steps')[0].actions.primary.onClick();
  (evaluateReport as jest.Mock).mockClear();

  // second step
  const loadSecondStep = () => shallow(node.prop('steps')[1].content);
  let secondStepNode = loadSecondStep();

  secondStepNode.find('.filterTile Button').at(0).simulate('click');
  secondStepNode = loadSecondStep();

  secondStepNode.find(NodeSelection).prop('addFilter')({
    type: 'executedFlowNodes',
    data: {},
    appliedTo: [],
  });

  return {node, secondStepNode: loadSecondStep()};
}
