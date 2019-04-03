/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ReportRenderer from './ReportRenderer';
import CombinedReportRenderer from './CombinedReportRenderer';

const reportTemplate = {
  combined: false,
  reportType: 'process',
  data: {
    processDefinitionKey: 'aKey',
    processDefinitionVersion: '1',
    view: {
      property: 'foo',
      entity: 'whatever'
    },
    groupBy: {
      type: 'bar'
    },
    visualization: 'number',
    configuration: {}
  },
  result: 1234
};

it('should render ProcessReportRenderer if the report type is process', () => {
  const node = shallow(<ReportRenderer report={reportTemplate} />);

  expect(node.find('ProcessReportRenderer')).toBePresent();
});

it('should render DecisionReportRenderer if the report type is decision', () => {
  const report = {
    ...reportTemplate,
    reportType: 'decision',
    data: {
      ...reportTemplate.data,
      decisionDefinitionKey: 'foo',
      decisionDefinitionVersion: '1'
    }
  };
  const node = shallow(<ReportRenderer report={report} />);

  expect(node.find('DecisionReportRenderer')).toBePresent();
});

it('should render CombinedReportRenderer if the report is combined', () => {
  const report = {
    ...reportTemplate,
    combined: true
  };
  const node = shallow(<ReportRenderer report={report} />);

  expect(node.find(CombinedReportRenderer)).toBePresent();
});

it('should display an error message the report is defined', () => {
  const report = null;
  const node = shallow(<ReportRenderer report={report} />);

  expect(node.find('Message')).toBePresent();
});

it('should include the instance count if indicated in the config', () => {
  const report = {
    ...reportTemplate,
    data: {
      ...reportTemplate.data,
      configuration: {showInstanceCount: true}
    },
    processInstanceCount: 723
  };

  const node = shallow(<ReportRenderer report={report} />);

  expect(node.find('.additionalInfo')).toBePresent();
  expect(node.find('.additionalInfo').text()).toContain('723');
});

it('should show an incomplete report notice when not in edit mode', () => {
  const node = shallow(<ReportRenderer report={{data: {}}} />);

  expect(node.find('IncompleteReport')).toBePresent();
});

describe('SetupNotice', () => {
  it('should instruct to add a process definition key if not available', () => {
    const newReport = {
      ...reportTemplate,
      data: {
        ...reportTemplate.data,
        processDefinitionKey: ''
      }
    };

    const node = shallow(<ReportRenderer report={newReport} updateReport />);

    expect(node).toMatchSnapshot();
  });

  it('should instruct to add a process definition version if not available', () => {
    const newReport = {
      ...reportTemplate,
      data: {
        ...reportTemplate.data,
        processDefinitionVersion: ''
      }
    };

    const node = shallow(<ReportRenderer report={newReport} updateReport />);

    expect(node).toMatchSnapshot();
  });

  it('should instruct to add view option if not available', () => {
    const newReport = {
      ...reportTemplate,
      data: {
        ...reportTemplate.data,
        view: null
      }
    };
    const node = shallow(<ReportRenderer report={newReport} updateReport />);

    expect(node).toMatchSnapshot();
  });

  it('should instruct to add group by option if not available', () => {
    const newReport = {
      ...reportTemplate,
      data: {
        ...reportTemplate.data,
        groupBy: null
      }
    };

    const node = shallow(<ReportRenderer report={newReport} updateReport />);

    expect(node).toMatchSnapshot();
  });

  it('should instruct to add visualization option if not available', () => {
    const newReport = {
      ...reportTemplate,
      data: {
        ...reportTemplate.data,
        visualization: null
      }
    };

    const node = shallow(<ReportRenderer report={newReport} updateReport />);

    expect(node).toMatchSnapshot();
  });

  it('should not add instruction for group by if operation is raw data', () => {
    const newReport = {
      ...reportTemplate,
      data: {
        ...reportTemplate.data,
        view: {
          property: 'rawData'
        }
      }
    };

    const node = shallow(<ReportRenderer report={newReport} updateReport />);

    expect(node.find('SetupNotice')).not.toBePresent();
  });
});
