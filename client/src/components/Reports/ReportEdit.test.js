/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ReportEdit from './ReportEdit';
import {incompatibleFilters, updateEntity, createEntity, evaluateReport} from 'services';

jest.mock('react-router-dom', () => {
  const rest = jest.requireActual('react-router-dom');
  return {
    ...rest,
    withRouter: a => a
  };
});

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    evaluateReport: jest.fn(),
    updateEntity: jest.fn(),
    createEntity: jest.fn(),
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

const props = {
  report,
  location: {pathname: '/report/1'}
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

  const node = shallow(<ReportEdit {...props} report={{...report, ...combinedReport}} />).dive();

  expect(node).not.toIncludeText('ControlPanel');
});

it('should contain a Control Panel in edit mode for a single report', () => {
  const node = shallow(<ReportEdit {...props} report={report} />).dive();

  expect(node).toIncludeText('ControlPanel');
});

it('should contain a decision control panel in edit mode for decision reports', () => {
  const node = shallow(
    <ReportEdit {...props} report={{...report, reportType: 'decision'}} />
  ).dive();

  expect(node).toIncludeText('DecisionControlPanel');
});

it('should update the report', async () => {
  const node = shallow(<ReportEdit {...props} report={report} />).dive();

  await node.instance().updateReport({visualization: {$set: 'customTestVis'}});

  expect(node.state().report.data.visualization).toBe('customTestVis');
});

it('should evaluate the report after updating', async () => {
  const node = shallow(<ReportEdit {...props} report={report} />).dive();

  evaluateReport.mockReturnValue(report);
  await node.instance().updateReport({visualization: {$set: 'customTestVis'}}, true);

  expect(evaluateReport).toHaveBeenCalled();
});

it('should reset the report data to its original state after canceling', async () => {
  const node = shallow(<ReportEdit {...props} report={report} />).dive();

  const dataBefore = node.state().report;

  await node.instance().updateReport({visualization: {$set: 'customTestVis'}});
  node.instance().cancel();

  expect(node.state().report).toEqual(dataBefore);
});

it('should save a changed report', async () => {
  const node = shallow(<ReportEdit {...props} report={report} />).dive();

  node.instance().save({}, 'new Name');

  expect(updateEntity).toHaveBeenCalled();
});

it('should reset name on cancel', async () => {
  const node = shallow(<ReportEdit {...props} report={report} />).dive();

  node.setState({report: {...report, name: 'new Name'}});

  node.instance().cancel();

  expect(node.state().report.name).toBe('name');
});

it('should use original data as result data if report cant be evaluated on cancel', async () => {
  const node = shallow(<ReportEdit {...props} report={report} />).dive();

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
  node.instance().cancel();

  expect(node.state().report.data.processDefinitionKey).toEqual('123');
});

it('should show a warning message when the data is not complete', async () => {
  const node = shallow(<ReportEdit {...props} report={report} />).dive();

  node.setState({
    report: {
      ...report,
      result: {data: new Array(1000), isComplete: false, processInstanceCount: 1500}
    }
  });

  expect(node.find('Message')).toExist();
  expect(node.find('Message').props().type).toBe('warning');
});

it('should show a warning message when there are incompatible filter ', async () => {
  const node = shallow(<ReportEdit {...props} report={report} />).dive();

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

  expect(node.find('Message')).toExist();
  expect(node.find('Message').props().type).toBe('warning');
});

it('should set conflict state when conflict happens on save button click', async () => {
  const conflictedItems = [{id: '1', name: 'alert', type: 'Alert'}];

  const mightFail = (promise, cb, err) =>
    err({statusText: 'Conflict', json: () => ({conflictedItems})});

  const node = shallow(
    <ReportEdit.WrappedComponent {...props} report={report} mightFail={mightFail} />
  );

  node.instance().save({});
  await node.update();

  expect(node.state().conflict.type).toEqual('save');
  expect(node.state().conflict.items).toEqual(conflictedItems);
});

it('should create a new report if the report is new', () => {
  const node = shallow(
    <ReportEdit.WrappedComponent
      {...props}
      report={report}
      mightFail={(promise, cb) => cb(promise)}
      updateOverview={jest.fn()}
      isNew
    />
  );

  node.instance().save();

  expect(createEntity).toHaveBeenCalledWith('report/process/single', {
    collectionId: null,
    data: report.data,
    name: report.name
  });
});

it('should create a new report in a collection', async () => {
  const node = await shallow(
    <ReportEdit.WrappedComponent
      {...props}
      location={{pathname: '/collection/123/report/new/edit'}}
      match={{params: {id: 'new'}}}
      report={report}
      mightFail={(promise, cb) => cb(promise)}
      updateOverview={jest.fn()}
      isNew
    />
  );

  node.instance().save();

  expect(createEntity).toHaveBeenCalledWith('report/process/single', {
    collectionId: '123',
    data: report.data,
    name: report.name
  });
});

it('should invoke updateOverview when saving the report', async () => {
  updateEntity.mockClear();
  updateEntity.mockReturnValue({});
  const spy = jest.fn();
  const node = shallow(<ReportEdit {...props} report={report} updateOverview={spy} />).dive();

  await node.instance().save();

  expect(spy).toHaveBeenCalled();
});

describe('showIncompleteResultWarning', () => {
  it('should show incomplete warning if report is configured and incomplete', () => {
    const node = shallow(<ReportEdit {...props} report={report} />).dive();

    node.setState({
      report: {
        ...report,
        result: {...report.result, isComplete: false}
      }
    });

    expect(node.instance().showIncompleteResultWarning()).toBe(true);
  });

  it('should not show incomplete data warning if the visualization is not selected', () => {
    const node = shallow(<ReportEdit {...props} report={report} />).dive();

    node.setState({
      report: {
        ...report,
        data: {
          ...report.data,
          visualization: null
        },
        result: {...report.result, isComplete: false}
      }
    });

    expect(node.instance().showIncompleteResultWarning()).toBe(false);
  });
});
