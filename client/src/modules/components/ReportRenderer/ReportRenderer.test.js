/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ReportRenderer from './ReportRenderer';
import CombinedReportRenderer from './CombinedReportRenderer';
import NoDataNotice from './NoDataNotice';
import IncompleteReport from './IncompleteReport';

const reportTemplate = {
  combined: false,
  reportType: 'process',
  data: {
    processDefinitionKey: 'aKey',
    processDefinitionVersions: ['1'],
    view: {
      property: 'foo',
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
    data: {
      ...reportTemplate.data,
      decisionDefinitionKey: 'foo',
      decisionDefinitionVersions: ['1'],
    },
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

it('should display an error message the report is defined', () => {
  const report = null;
  const node = shallow(<ReportRenderer report={report} />);

  expect(node.find('MessageBox')).toExist();
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

it('should show an incomplete report notice when inside a dashboard', () => {
  const node = shallow(<ReportRenderer report={{data: {}}} context="dashboard" />);

  expect(node.find(IncompleteReport)).toExist();
});

describe('SetupNotice', () => {
  it('should instruct to click the edit button in report view', () => {
    const newReport = {data: {}};

    const node = shallow(<ReportRenderer report={newReport} />);

    expect(node).toMatchSnapshot();
  });

  it('should instruct to add a process definition key if not available', () => {
    const newReport = {
      ...reportTemplate,
      data: {
        ...reportTemplate.data,
        processDefinitionKey: '',
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
        processDefinitionVersions: [],
      },
    };

    const node = shallow(<ReportRenderer report={newReport} updateReport />);

    expect(node).toMatchSnapshot();
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
          property: 'rawData',
        },
      },
    };

    const node = shallow(<ReportRenderer report={newReport} updateReport />);

    expect(node.find('SetupNotice')).not.toExist();
  });
});

describe('NoDataNotice', () => {
  it('should show a no data notice if the result does not contain process instances', () => {
    const report = {
      ...reportTemplate,
      result: {
        ...reportTemplate.result,
        instanceCount: 0,
      },
    };

    const node = shallow(<ReportRenderer report={report} />);

    expect(node.find(NoDataNotice)).toExist();
  });

  it('should not show a no data notice for single number frequency reports', () => {
    const report = {
      ...reportTemplate,
      data: {
        ...reportTemplate.data,
        view: {
          property: 'frequency',
          entity: 'processInstance',
        },
        groupBy: {
          type: 'none',
        },
        visualization: 'number',
      },
      result: {
        ...reportTemplate.result,
        instanceCount: 0,
      },
    };

    const node = shallow(<ReportRenderer report={report} />);

    expect(node.find(NoDataNotice)).not.toExist();
  });

  it('should show a no data notice if a combined report contains no data', () => {
    const report = {
      ...reportTemplate,
      data: {
        ...reportTemplate.data,
        reports: [1, 2],
      },
      result: {
        data: {
          1: {
            ...reportTemplate,
            result: {
              ...reportTemplate.result,
              instanceCount: 0,
            },
          },
          2: {
            ...reportTemplate,
            result: {
              ...reportTemplate.result,
              instanceCount: 0,
            },
          },
        },
      },
      combined: true,
    };

    const node = shallow(<ReportRenderer report={report} />);

    expect(node.find(NoDataNotice)).toExist();
  });

  it('should not show a no data notice if a combined report contains at least one report with data', () => {
    const report = {
      ...reportTemplate,
      data: {
        ...reportTemplate.data,
        reports: [1, 2],
      },
      result: {
        data: {
          1: {
            ...reportTemplate,
            result: {
              ...reportTemplate.result,
              instanceCount: 0,
            },
          },
          2: {
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

    expect(node.find(NoDataNotice)).not.toExist();
  });

  it('should show a no data notice if the reports contains process instances, but no meaningful datapoints', () => {
    const report = {
      ...reportTemplate,
      result: {
        ...reportTemplate.result,
        type: 'map',
        data: [],
      },
    };

    const node = shallow(<ReportRenderer report={report} />);

    expect(node.find(NoDataNotice)).toExist();
  });

  it('should show a no data notice for empty hyper report', () => {
    const report = {
      ...reportTemplate,
      result: {
        ...reportTemplate.result,
        type: 'hyperMap',
        data: [
          {key: 'true', value: []},
          {key: 'false', value: []},
        ],
      },
    };

    const node = shallow(<ReportRenderer report={report} />);

    expect(node.find(NoDataNotice)).toExist();
  });
});
