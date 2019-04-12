/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ReportEdit from './ReportEdit';

import {evaluateReport, saveReport} from './service';
import {incompatibleFilters} from 'services';
import {addNotification} from 'notifications';

jest.mock('./service', () => {
  return {
    evaluateReport: jest.fn(),
    saveReport: jest.fn()
  };
});

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    incompatibleFilters: jest.fn()
  };
});

jest.mock('notifications', () => ({addNotification: jest.fn()}));

const report = {
  id: '1',
  name: 'name',
  lastModifier: 'lastModifier',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reportType: 'process',
  combined: false,
  data: {
    processDefinitionKey: null,
    configuration: {},
    parameters: {},
    visualization: 'table'
  },
  result: {data: [1, 2, 3]}
};

it('should not contain a Control Panel in edit mode for a combined report', () => {
  const combinedReport = {
    combined: true,
    result: {
      data: {
        test: {
          data: {
            visualization: 'test'
          }
        }
      }
    }
  };

  const node = shallow(<ReportEdit report={{...report, ...combinedReport}} />).dive();

  expect(node).not.toIncludeText('ControlPanel');
});

it('should contain a Control Panel in edit mode for a single report', () => {
  const node = shallow(<ReportEdit report={report} />).dive();

  expect(node).toIncludeText('ControlPanel');
});

it('should contain a decision control panel in edit mode for decision reports', () => {
  const node = shallow(<ReportEdit report={{...report, reportType: 'decision'}} />).dive();

  expect(node).toIncludeText('DecisionControlPanel');
});

it('should update the report', async () => {
  const node = shallow(<ReportEdit report={report} />).dive();

  await node.instance().updateReport({visualization: {$set: 'customTestVis'}});

  expect(node.state().report.data.visualization).toBe('customTestVis');
});

it('should evaluate the report after updating', async () => {
  const node = shallow(<ReportEdit report={report} />).dive();

  evaluateReport.mockClear();
  await node.instance().updateReport({visualization: {$set: 'customTestVis'}}, true);

  expect(evaluateReport).toHaveBeenCalled();
});

it('should reset the report data to its original state after canceling', async () => {
  const node = shallow(<ReportEdit report={report} />).dive();

  const dataBefore = node.state().report;

  await node.instance().updateReport({visualization: {$set: 'customTestVis'}});
  await node.instance().cancel();

  expect(node.state().report).toEqual(dataBefore);
});

it('should save a changed report', async () => {
  const node = shallow(<ReportEdit report={report} />).dive();

  await node.instance().save({}, 'new Name');

  expect(saveReport).toHaveBeenCalled();
});

it('should reset name on cancel', async () => {
  const node = shallow(<ReportEdit report={report} />).dive();

  node.setState({report: {...report, name: 'new Name'}});

  await node.instance().cancel();

  expect(node.state().report.name).toBe('name');
});

it('should use original data as result data if report cant be evaluated on cancel', async () => {
  const node = shallow(<ReportEdit report={report} />).dive();

  node.setState({
    originalData: {
      ...report,
      data: {
        processDefinitionKey: '123',
        configuration: {}
      }
    }
  });

  evaluateReport.mockReturnValueOnce(null);
  await node.instance().cancel();

  expect(node.state().report.data.processDefinitionKey).toEqual('123');
});

it('should show a warning message when the data is not complete', async () => {
  const node = shallow(<ReportEdit report={report} />).dive();

  node.setState({
    report: {
      result: {data: new Array(1000), isComplete: false, processInstanceCount: 1500}
    }
  });

  expect(node.find('Message')).toBePresent();
  expect(node.find('Message').props().type).toBe('warning');
});

it('should show a warning message when there are incompatible filter ', async () => {
  const node = shallow(<ReportEdit report={report} />).dive();

  incompatibleFilters.mockReturnValue(true);

  node.setState({
    report: {
      ...report,
      data: {
        visualization: 'table',
        view: {
          property: 'rawData'
        },
        filter: ['some data']
      }
    }
  });

  expect(node.find('Message')).toBePresent();
  expect(node.find('Message').props().type).toBe('warning');
});

it('should set conflict state when conflict happens on save button click', async () => {
  const conflictedItems = [{id: '1', name: 'alert', type: 'Alert'}];
  saveReport.mockImplementation(async () => {
    const error = {statusText: 'Conflict', json: async () => ({conflictedItems})};
    throw error;
  });

  const node = shallow(<ReportEdit report={report} />).dive();

  await node.instance().save({});
  await node.update();

  expect(node.state().conflict.type).toEqual('Save');
  expect(node.state().conflict.items).toEqual(conflictedItems);
});

it('should invok updateOverview when saving the report', async () => {
  saveReport.mockClear();
  saveReport.mockReturnValue({});
  const spy = jest.fn();
  const node = shallow(<ReportEdit report={report} updateOverview={spy} />).dive();

  await node.instance().save();

  expect(spy).toHaveBeenCalled();
});

it('should render collections dropdown', async () => {
  const node = shallow(<ReportEdit report={report} />).dive();

  expect(node.find('CollectionsDropdown')).toBePresent();
});
