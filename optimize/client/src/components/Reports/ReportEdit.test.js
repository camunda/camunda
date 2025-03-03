/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from 'react';
import {shallow} from 'enzyme';
import {useHistory, useLocation} from 'react-router-dom';

import {updateEntity, createEntity, evaluateReport} from 'services';
import {nowDirty, nowPristine} from 'saveGuard';
import {EntityNameForm, InstanceCount, ReportRenderer} from 'components';
import {track} from 'tracking';

import {ReportEdit} from './ReportEdit';
import ReportControlPanel from './controlPanels/ReportControlPanel';
import {useErrorHandling} from 'hooks';

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useLocation: jest.fn().mockReturnValue({pathname: '/report/1'}),
  useHistory: jest.fn().mockReturnValue({push: jest.fn()}),
}));

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    evaluateReport: jest.fn(),
    updateEntity: jest.fn(),
    createEntity: jest.fn(),
    incompatibleFilters: jest.fn(),
  };
});

jest.mock('notifications', () => ({addNotification: jest.fn()}));
jest.mock('saveGuard', () => ({nowDirty: jest.fn(), nowPristine: jest.fn()}));
jest.mock('tracking', () => ({track: jest.fn()}));
jest.mock('hooks', () => ({
  useChangedState: jest.requireActual('react-18').useState,
  useErrorHandling: jest.fn(() => ({
    mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  })),
}));

const mockSessionStorage = {
  getItem: jest.fn(),
  setItem: jest.fn(),
  removeItem: jest.fn(),
  clear: jest.fn(),
};

Object.defineProperty(window, 'sessionStorage', {
  value: mockSessionStorage,
  writable: true,
});

const report = {
  id: '1',
  name: 'name',
  description: 'description',
  lastModifier: 'lastModifier',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  data: {
    definitions: [
      {
        key: 'aKey',
        versions: ['aVersion'],
        tenantIds: [],
      },
    ],
    configuration: {
      tableColumns: {
        columnOrder: ['a', 'b', 'c'],
      },
    },
    view: {proeprty: 'rawData', entity: null},
    groupBy: {type: 'none', value: null},
    visualization: 'table',
    processDefinitionKey: 'key',
  },
  result: {data: [1, 2, 3], instanceCount: 37},
};

const props = {
  report,
  updateOverview: jest.fn(),
};

const mightFail = jest.fn((promise, cb) => cb(promise));
useErrorHandling.mockReturnValue({mightFail});

it('should show the instance count in the header if it is available', () => {
  const node = shallow(<ReportEdit {...props} />);

  expect(node.find(InstanceCount)).toExist();
});

it('should contain a Control Panel in edit mode for a single report', () => {
  const node = shallow(<ReportEdit {...props} />);

  expect(node).toIncludeText('ControlPanel');
});

it('should update the report', () => {
  const node = shallow(<ReportEdit {...props} />);

  node.find('Visualization').simulate('change', {visualization: {$set: 'customTestVis'}}, true);

  expect(node.find('Visualization').prop('report').visualization).toBe('customTestVis');
});

it('should evaluate the report on mount if the config is complete, but the result is missing', () => {
  evaluateReport.mockClear();
  evaluateReport.mockReturnValue(report);

  const node = shallow(<ReportEdit {...props} report={{...report, result: null}} />);
  node.find('.updatePreview').simulate('toggle', true);

  runAllEffects();

  expect(node.find('Loading')).toExist();
  expect(evaluateReport).toHaveBeenCalled();
});

it('should evaluate the report after updating', () => {
  const node = shallow(<ReportEdit {...props} />);

  node.find('.updatePreview').simulate('toggle', true);
  evaluateReport.mockReturnValue(report);

  node.find('Visualization').simulate('change', {visualization: {$set: 'customTestVis'}}, true);

  expect(evaluateReport).toHaveBeenCalled();
});

it('should not evaluate the report if the view/groupBy/visualization setting is incomplete', () => {
  const node = shallow(<ReportEdit {...props} />);

  evaluateReport.mockClear();
  node.find('Visualization').simulate('change', {$unset: ['groupBy', 'visualization']}, true);

  expect(evaluateReport).not.toHaveBeenCalled();
});

it('should reset the report data to its original state after canceling', async () => {
  const node = shallow(<ReportEdit {...props} />);

  const dataBefore = node.find('Configuration').prop(report);

  node.find('Visualization').simulate('change', {visualization: {$set: 'customTestVis'}}, true);
  node.find('EntityNameForm').simulate('cancel');

  expect(node.find('Configuration').prop(report)).toEqual(dataBefore);
});

it('should save a changed report', async () => {
  const node = shallow(<ReportEdit {...props} />);

  node.find('EntityNameForm').simulate('save');

  expect(updateEntity).toHaveBeenCalled();
});

it('should reset name and description on cancel', async () => {
  const node = shallow(<ReportEdit {...props} />);

  node
    .find('Visualization')
    .simulate('change', {$set: {name: 'new Name', description: 'new Description'}}, true);
  node.find('EntityNameForm').simulate('cancel');

  expect(node.find('EntityNameForm').prop('name')).toBe('name');
  expect(node.find('EntityNameForm').prop('description')).toBe('description');
});

it('should use original data as result data if report cant be evaluated on cancel', async () => {
  const node = shallow(<ReportEdit {...props} />);

  node
    .find('Visualization')
    .simulate(
      'change',
      {$set: {definitions: [{key: 123, versions: ['1'], tenantIds: [null]}]}},
      true
    );

  node.find('EntityNameForm').simulate('cancel');

  expect(node.find('Configuration').prop('report').data.definitions[0].key).toEqual('aKey');
});

it('should set conflict state when conflict happens on save button click', async () => {
  const conflictedItems = [{id: '1', name: 'alert', type: 'alert'}];

  mightFail.mockImplementationOnce((_promise, _cb, err) => err({status: 409, conflictedItems}));

  const node = shallow(<ReportEdit {...props} />);

  try {
    node.find('EntityNameForm').simulate('save');
  } catch (_e) {
    expect(node.find('ConflictModal').prop('conflict').type).toEqual('save');
    expect(node.find('ConflictModal').prop('conflict').items).toEqual(conflictedItems);
  }
});

it('should create a new report if the report is new', () => {
  const node = shallow(<ReportEdit {...props} isNew />);

  node.find('EntityNameForm').simulate('save');

  expect(createEntity).toHaveBeenCalledWith('report/process/single', {
    collectionId: null,
    data: report.data,
    name: report.name,
    description: report.description,
  });
});

it('should create a new report in a collection', async () => {
  useLocation.mockReturnValue({pathname: '/collection/123/report/new/edit'});
  const node = await shallow(<ReportEdit {...props} match={{params: {id: 'new'}}} isNew />);

  node.find('EntityNameForm').simulate('save');

  expect(createEntity).toHaveBeenCalledWith('report/process/single', {
    collectionId: '123',
    data: report.data,
    name: report.name,
    description: report.description,
  });
});

it('should invoke updateOverview when saving the report', async () => {
  evaluateReport.mockReturnValue({...report, id: 'change'});
  const spy = jest.fn();
  const node = shallow(<ReportEdit {...props} updateOverview={spy} />);

  node.find('EntityNameForm').simulate('save');
  await flushPromises();

  expect(spy).toHaveBeenCalled();
});

it('should notify the saveGuard of changes', async () => {
  const node = shallow(<ReportEdit {...props} />);

  await node.find(ReportControlPanel).prop('updateReport')({processDefinitionKey: {$set: 'b'}});

  await runAllEffects();
  await flushPromises();
  await runAllEffects();
  await flushPromises();

  expect(nowDirty).toHaveBeenCalled();

  node.find(ReportControlPanel).prop('updateReport')({processDefinitionKey: {$set: 'key'}});

  expect(nowPristine).toHaveBeenCalled();
});

it('should only resolve the save promise if a decision for conflicts has been made', async () => {
  nowDirty.mockClear();
  evaluateReport.mockReturnValue(report);

  const mightFail = jest.fn().mockImplementation((promise, cb) => cb(promise));
  useErrorHandling.mockReturnValue({
    mightFail,
  });
  const node = shallow(<ReportEdit {...props} />);

  mightFail.mockImplementationOnce((_promise, _cb, err) =>
    err({status: 409, conflictedItems: [{id: '1', name: 'alert', type: 'alert'}]})
  );

  let promiseResolved = false;
  node
    .find(EntityNameForm)
    .prop('onSave')()
    .then(() => {
      promiseResolved = true;
    });

  expect(promiseResolved).toBe(false);
  expect(node.find('ConflictModal').prop('conflict')).not.toBe(null);

  node.find('ConflictModal').simulate('confirm');

  await flushPromises();

  expect(promiseResolved).toBe(true);
});

it('should go back to a custom route after saving if provided as URL Search Param', async () => {
  const spy = {push: jest.fn()};
  useHistory.mockReturnValue(spy);
  useLocation.mockReturnValue({pathname: '/report/1', search: '?returnTo=/dashboard/1/edit'});
  const node = shallow(<ReportEdit {...props} />);

  await node.find(EntityNameForm).prop('onSave')();

  expect(spy.push).toHaveBeenCalledWith('/dashboard/1/edit');
});

it('should go back to a custom route after canceling if provided as URL Search Param', async () => {
  const spy = {push: jest.fn()};
  useHistory.mockReturnValue(spy);
  useLocation.mockReturnValue({pathname: '/report/1', search: '?returnTo=/dashboard/1/edit'});
  const node = shallow(<ReportEdit {...props} />);

  node.find(EntityNameForm).prop('onCancel')({preventDefault: jest.fn()});

  expect(spy.push).toHaveBeenCalledWith('/dashboard/1/edit');
});

it('should show loading indicator if specified by children components', () => {
  const node = shallow(<ReportEdit {...props} />);
  node.find('.updatePreview').simulate('toggle', true);
  node.find(ReportControlPanel).prop('setLoading')(true);

  expect(node.find('Loading')).toExist();

  node.find(ReportControlPanel).prop('setLoading')(false);

  expect(node.find('Loading')).not.toExist();
});

it('should pass the error to reportRenderer if evaluation fails', async () => {
  const testError = {status: 400, message: 'testError', reportDefinition: report};
  const mightFail = (_promise, _cb, err) => err(testError);
  useErrorHandling.mockReturnValueOnce({
    mightFail,
  });
  const node = shallow(<ReportEdit {...props} />);
  await node.find(ReportRenderer).prop('loadReport')(undefined, {...report, name: 'change'});

  expect(node.find(ReportRenderer).prop('error')).toEqual(testError);
});

it('should show update preview switch disabled by default', () => {
  const node = shallow(<ReportEdit {...props} />);

  const updateSwicth = node.find('.updatePreview');

  expect(sessionStorage.getItem).toHaveBeenCalledWith('shouldAutoReloadPreview');
  expect(updateSwicth.prop('labelText')).toBe('Update preview automatically');
  expect(updateSwicth.prop('toggled')).toBe(false);
  expect(node.find('.RunPreviewButton')).toExist();
});

it('should turn off automatic update when switch is toggled', () => {
  const node = shallow(<ReportEdit {...props} />);

  node.find('.updatePreview').simulate('toggle', false);

  expect(node.find('.updatePreview').prop('toggled')).toBe(false);
  expect(sessionStorage.setItem).toHaveBeenCalledWith('shouldAutoReloadPreview', false);
  expect(node.find('.RunPreviewButton')).toExist();
});

it('should use sessionStorage value to set the preview toggle', () => {
  window.sessionStorage.getItem.mockReturnValueOnce('true');
  const node = shallow(<ReportEdit {...props} />);

  expect(node.find('.updatePreview').prop('toggled')).toBe(true);
  expect(node.find('.RunPreviewButton')).not.toExist();
});

it('should re-evalueate report on run button click', () => {
  const node = shallow(<ReportEdit {...props} />);

  node.find('.updatePreview').simulate('toggle', false);
  const runButton = node.find('.RunPreviewButton');
  runButton.simulate('click');

  expect(evaluateReport).toHaveBeenCalledWith(props.report, [], {});
});

it('should not call evaluateReport when auto update is off', () => {
  evaluateReport.mockClear();
  const node = shallow(<ReportEdit {...props} />);
  node.find('.updatePreview').simulate('toggle', false);

  node.find(ReportControlPanel).prop('updateReport')({processDefinitionKey: {$set: 'b'}}, true);

  expect(evaluateReport).not.toHaveBeenCalled();
});

it('should update description', async () => {
  const node = shallow(<ReportEdit {...props} />);

  node.find('EntityNameForm').simulate('descriptionChange', 'some description');
  node.find('EntityNameForm').simulate('save');

  expect(node.find('EntityNameForm').prop('description')).toEqual('some description');
  expect(track).toHaveBeenCalledWith('editDescription', {entity: 'report', entityId: '1'});
  expect(updateEntity).toHaveBeenCalledWith(
    'report/process/single',
    '1',
    {
      data: {
        configuration: {
          tableColumns: {columnOrder: ['a', 'b', 'c']},
        },
        definitions: [{key: 'aKey', tenantIds: [], versions: ['aVersion']}],
        groupBy: {type: 'none', value: null},
        processDefinitionKey: 'key',
        view: {entity: null, proeprty: 'rawData'},
        visualization: 'table',
      },
      description: 'some description',
      name: 'name',
    },
    {query: {force: false}}
  );
});

it('should update local report copy when column rearangement is updated', () => {
  const node = shallow(<ReportEdit {...props} />);

  node
    .find('Visualization')
    .simulate('change', {configuration: {tableColumns: {columnOrder: {$set: ['c', 'a', 'b']}}}});

  expect(evaluateReport).not.toHaveBeenCalled();

  node.find('.RunPreviewButton').simulate('click');

  expect(evaluateReport).toHaveBeenCalledWith(
    {
      ...report,
      data: {...report.data, configuration: {tableColumns: {columnOrder: ['c', 'a', 'b']}}},
    },
    [],
    {}
  );
});

it('should hide bottom raw data panel for table reports', async () => {
  const node = await shallow(<ReportEdit {...props} />);

  await node.update();

  expect(node.find('.bottomPanel')).not.toExist();
});

it('should hide bottom raw data panel for empty reports', async () => {
  const node = await shallow(
    <ReportEdit
      {...props}
      report={{...report, result: undefined, data: {...report.data, visualization: 'number'}}}
    />
  );

  expect(node.find('.bottomPanel')).not.toExist();
});

it('should hide expandButton & report content when expanding bottom panel', async () => {
  const node = await shallow(
    <ReportEdit {...props} report={{...report, data: {...report.data, visualization: 'number'}}} />
  );

  const collapsibleContainer = node.find('CollapsibleContainer').dive();

  collapsibleContainer.find('.expandButton').simulate('click');
  collapsibleContainer.find('.expandButton').simulate('click');

  expect(collapsibleContainer.find('.expandButton')).not.toExist();
  expect(node.find('.Report__content').hasClass('hidden')).toBe(true);
});
