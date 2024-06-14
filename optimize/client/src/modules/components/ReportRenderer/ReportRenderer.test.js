/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ReportRenderer from './ReportRenderer';
import CombinedReportRenderer from './CombinedReportRenderer';
import NoDataNotice from './NoDataNotice';
import SetupNotice from './SetupNotice';

const reportTemplate = {
  combined: false,
  reportType: 'process',
  data: {
    definitions: [
      {
        key: 'aKey',
        versions: ['1'],
        tenantIds: [null],
      },
    ],
    view: {
      properties: ['foo'],
      entity: 'whatever',
    },
    groupBy: {
      type: 'bar',
    },
    visualization: 'number',
    configuration: {},
  },
  result: {data: 1234, instanceCount: 100},
};

it('should render ProcessReportRenderer if the report type is process', () => {
  const node = shallow(<ReportRenderer report={reportTemplate} />);

  expect(node.find('ProcessReportRenderer')).toExist();
});

it('should render DecisionReportRenderer if the report type is decision', () => {
  const report = {
    ...reportTemplate,
    reportType: 'decision',
  };
  const node = shallow(<ReportRenderer report={report} />);

  expect(node.find('DecisionReportRenderer')).toExist();
});

it('should render CombinedReportRenderer if the report is combined and all reports contain data', () => {
  const report = {
    ...reportTemplate,
    data: {
      ...reportTemplate.data,
      reports: ['reportId1'],
    },
    result: {
      data: {
        reportId1: {
          ...reportTemplate,
          result: {
            ...reportTemplate.result,
            instanceCount: 1,
          },
        },
      },
    },
    combined: true,
  };
  const node = shallow(<ReportRenderer report={report} />);

  expect(node.find(CombinedReportRenderer)).toExist();
});

it('should include the instance count if indicated in the config', () => {
  const report = {
    ...reportTemplate,
    data: {
      ...reportTemplate.data,
      configuration: {showInstanceCount: true},
    },
    result: {...reportTemplate.result, instanceCount: 723},
  };

  const node = shallow(<ReportRenderer report={report} />);

  expect(node.find('.additionalInfo')).toExist();
  expect(node.find('.additionalInfo').html()).toContain('723');
});

describe('SetupNotice', () => {
  it('should instruct to add a process definition key if not available', () => {
    const newReport = {
      ...reportTemplate,
      data: {
        ...reportTemplate.data,
        definitions: [
          {
            key: '',
            versions: null,
          },
        ],
      },
    };

    const node = shallow(<ReportRenderer report={newReport} updateReport />);

    expect(node).toMatchSnapshot();
  });

  it('should instruct to add a process definition version if not available', () => {
    const newReport = {
      ...reportTemplate,
      data: {
        ...reportTemplate.data,
        definitions: [
          {
            key: 'aKey',
            versions: [],
          },
        ],
      },
    };

    const node = shallow(<ReportRenderer report={newReport} updateReport />);

    expect(node).toMatchSnapshot();
  });

  it('should show setup notice if there are no tenants', () => {
    const newReport = {
      ...reportTemplate,
      data: {
        ...reportTemplate.data,
        definitions: [
          {
            key: 'aKey',
            versions: ['all'],
            tenantIds: [],
          },
        ],
      },
    };

    const node = shallow(<ReportRenderer report={newReport} updateReport />);

    expect(node.find(SetupNotice)).toExist();
  });

  it('should instruct to add view option if not available', () => {
    const newReport = {
      ...reportTemplate,
      data: {
        ...reportTemplate.data,
        view: null,
      },
    };
    const node = shallow(<ReportRenderer report={newReport} updateReport />);

    expect(node).toMatchSnapshot();
  });

  it('should instruct to add group by option if not available', () => {
    const newReport = {
      ...reportTemplate,
      data: {
        ...reportTemplate.data,
        groupBy: null,
      },
    };

    const node = shallow(<ReportRenderer report={newReport} updateReport />);

    expect(node).toMatchSnapshot();
  });

  it('should instruct to add visualization option if not available', () => {
    const newReport = {
      ...reportTemplate,
      data: {
        ...reportTemplate.data,
        visualization: null,
      },
    };

    const node = shallow(<ReportRenderer report={newReport} updateReport />);

    expect(node).toMatchSnapshot();
  });

  it('should instruct to select one or more reports if no reports are selected for combined reports', () => {
    const report = {
      combined: true,
      data: {
        configuration: {},
        reports: [],
      },
    };

    const node = shallow(<ReportRenderer report={report} updateReport />);

    expect(node).toMatchSnapshot();
  });

  it('should not add instruction for group by if property is raw data', () => {
    const newReport = {
      ...reportTemplate,
      data: {
        ...reportTemplate.data,
        view: {
          properties: ['rawData'],
        },
      },
    };

    const node = shallow(<ReportRenderer report={newReport} updateReport />);

    expect(node.find('SetupNotice')).not.toExist();
  });
});

describe('NoDataNotice', () => {
  it('should display an error message if the report is not defined', () => {
    const report = null;
    const node = shallow(<ReportRenderer report={report} />);

    expect(node.find({type: 'error'})).toExist();
  });

  it('should display warning notice if report is incomplete in view mode', () => {
    const node = shallow(<ReportRenderer report={{data: {}}} context="dashboard" />);

    expect(node.find({type: 'warning'})).toExist();
  });

  it('should display an info notice to edit setup when evaluation fails on preconfigured report', () => {
    const report = {
      ...reportTemplate,
      result: undefined,
    };

    const node = shallow(<ReportRenderer report={report} />);

    expect(node.find({type: 'info'})).toExist();
  });

  it('should display a notice based on error type if evaluation fails while loading the report', () => {
    const node = shallow(
      <ReportRenderer
        report={undefined}
        error={{status: 400, data: {errorMessage: 'test error'}}}
      />
    );

    expect(node).toMatchSnapshot();
  });

  it('should not show a no data notice for fully configured reports', () => {
    const node = shallow(<ReportRenderer report={reportTemplate} />);

    expect(node.find(NoDataNotice)).not.toExist();
  });
});
