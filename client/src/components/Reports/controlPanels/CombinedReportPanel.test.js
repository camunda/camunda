/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {loadReports, getCollection} from 'services';
import CombinedReportPanel from './CombinedReportPanel';

jest.mock('react-router-dom', () => {
  const rest = jest.requireActual('react-router-dom');
  return {
    ...rest,
    withRouter: (a) => a,
  };
});

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    formatters: {
      getHighlightedText: (text) => text,
    },
    loadReports: jest.fn(),
    getCollection: jest.fn(),
  };
});

const singleReportData = {
  processDefinitionKey: 'leadQualification',
  processDefinitionVersion: '1',
  view: {
    entity: 'flowNode',
    properties: ['duration'],
  },
  groupBy: {
    type: 'flowNodes',
    value: null,
  },
  distributedBy: {type: 'none'},
  configuration: {
    groupByDateVariableUnit: 'day',
    customBucket: {
      active: true,
      bucketSize: '10',
      baseline: '-10',
    },
    aggregationTypes: [{type: 'avg', value: null}],
  },
  visualization: 'bar',
};

const reportsList = [
  {
    id: 'singleReport',
    name: 'Single Report',
    combined: false,
    collectionId: null,
    reportType: 'process',
    data: singleReportData,
  },
  {
    id: 'combinedReport',
    name: 'Combined Report',
    combined: true,
    data: {
      configuration: {},
      reports: [{id: 'singleReport'}],
      visualization: 'bar',
    },
    result: {
      data: {
        singleReport: {
          id: 'singleReport',
          data: singleReportData,
        },
      },
    },
  },
  {
    id: 'anotherSingleReport',
    name: 'Another Single Report',
    combined: false,
    collectionId: null,
    reportType: 'process',
    data: {
      ...singleReportData,
      groupBy: {
        type: 'variable',
        value: {
          type: 'Date',
        },
      },
    },
  },
  {
    id: 'reportInAnotherCollection',
    name: 'Report in Another Collection',
    combined: false,
    collectionId: '123',
    reportType: 'process',
    data: {
      ...singleReportData,
      groupBy: {
        type: 'variable',
        value: {
          type: 'Integer',
        },
      },
    },
  },
  {
    id: 'flow-node-report',
    name: 'heatmap report',
    combined: false,
    data: {
      processDefinitionKey: 'leadQualification',
      processDefinitionVersion: '1',
      view: {
        entity: 'flowNode',
        properties: ['duration'],
      },
      groupBy: {
        type: 'flowNodes',
        value: null,
      },
      visualization: 'heat',
    },
  },
  {
    id: 'variableViewReport',
    name: 'Variable View Report',
    combined: false,
    collectionId: null,
    reportType: 'process',
    data: {
      ...singleReportData,
      view: {
        entity: 'variable',
        properties: [{name: 'doubleVar', type: 'Double'}],
      },
      groupBy: {type: 'none', value: null},
      visualization: 'number',
    },
  },
];

loadReports.mockReturnValue(reportsList);

const props = {
  updateReport: jest.fn(),
  configuration: {},
  report: reportsList[1],
  location: '/report/1',
};

it('should invoke loadReports to load all reports when it is mounted', async () => {
  const node = await shallow(<CombinedReportPanel {...props} />);
  await node.update();

  expect(loadReports).toHaveBeenCalledWith(null);
});

it('should invoke loadReports with the collection id when available', async () => {
  getCollection.mockReturnValueOnce('collectionId');
  const node = await shallow(<CombinedReportPanel {...props} />);
  await node.update();

  expect(loadReports).toHaveBeenCalledWith('collectionId');
});

it('should have input checkbox for only single report items in the list', async () => {
  const node = await shallow(<CombinedReportPanel {...props} />);
  await node.update();
  expect(JSON.stringify(node.find('TypeaheadMultipleSelection').props())).toMatch('singleReport');
  expect(JSON.stringify(node.find('TypeaheadMultipleSelection').props())).not.toMatch(
    'Combined Report'
  );
});

describe('isCompatible', () => {
  let node = {};
  beforeEach(async () => {
    node = await shallow(<CombinedReportPanel {...props} />);
    await node.update();
  });

  it('should only allow to combine if both reports has the same visualization', async () => {
    const tableReport = {
      ...reportsList[0],
      data: {
        ...reportsList[0].data,
        visualization: 'table',
      },
    };

    expect(node.instance().isCompatible(tableReport)).toBeFalsy();
  });

  it('should only allow to combine if both reports has the same groupBy', async () => {
    const groupByNone = {
      ...reportsList[0],
      data: {
        ...reportsList[0].data,
        groupBy: {
          type: 'none',
          value: null,
        },
      },
    };

    expect(node.instance().isCompatible(groupByNone)).toBeFalsy();
  });

  it('should only allow to combine if both reports has the different view property', async () => {
    const reportSameProperty = {
      ...reportsList[0],
      data: {
        ...reportsList[0].data,
        view: {
          entity: 'flowNode',
          properties: ['duration'],
        },
      },
    };

    expect(node.instance().isCompatible(reportSameProperty)).toBeTruthy();
  });

  it('should only allow to combine if both reports has the same view entity and property', async () => {
    const reportSameProperty = {
      ...reportsList[0],
      data: {
        ...reportsList[0].data,
        view: {
          entity: 'process instance',
          properties: ['frequency'],
        },
      },
    };

    expect(node.instance().isCompatible(reportSameProperty)).toBeFalsy();
  });

  it('should only allow to combine if both reports have the same date variable grouping', async () => {
    const combinedReport = {
      ...reportsList[1],
      result: {
        ...reportsList[1].result,
        data: {
          singleReport: {
            id: 'singleReport',
            data: reportsList[2].data,
          },
        },
      },
    };
    node.setProps({report: combinedReport});
    const reportWithDifferentDateConfiguration = {
      ...reportsList[2],
      data: {
        ...reportsList[2].data,
        configuration: {
          groupByDateVariableUnit: 'month',
        },
      },
    };

    expect(node.instance().isCompatible(reportWithDifferentDateConfiguration)).toBeFalsy();
  });

  it('should only allow to combine if both reports have the same number variable grouping', async () => {
    const combinedReport = {
      ...reportsList[1],
      result: {
        ...reportsList[1].result,
        data: {
          singleReport: {
            id: 'singleReport',
            data: reportsList[3].data,
          },
        },
      },
    };
    node.setProps({report: combinedReport});
    const reportWithDifferentDateConfiguration = {
      ...reportsList[3],
      data: {
        ...reportsList[3].data,
        configuration: {
          customBucket: {
            active: true,
            bucketSize: '5',
            baseline: '0',
          },
        },
      },
    };

    expect(node.instance().isCompatible(reportWithDifferentDateConfiguration)).toBeFalsy();
  });

  it('should allow to combine if both reports have variable view', async () => {
    const referenceReport = reportsList.find(({id}) => id === 'variableViewReport');

    node = await shallow(
      <CombinedReportPanel
        {...props}
        report={{
          id: 'combinedReport',
          name: 'Combined Report',
          combined: true,
          data: {
            configuration: {},
            reports: [{id: 'variableViewReport'}],
            visualization: 'bar',
          },
          result: {
            data: {
              variableViewReport: {
                id: 'variableViewReport',
                data: referenceReport.data,
              },
            },
          },
        }}
      />
    );
    await node.update();

    const reportWithDifferentViewVariable = {
      ...referenceReport,
      data: {
        ...referenceReport.data,
        view: {
          entity: 'variable',
          properties: [{name: 'longVar', type: 'Long'}],
        },
      },
    };

    expect(node.instance().isCompatible(reportWithDifferentViewVariable)).toBe(true);
  });

  it('should allow to combined a userTask duration report with a flowNode duration report', async () => {
    const report = {
      ...reportsList[0],
      data: {
        ...reportsList[0].data,
        view: {
          entity: 'userTask',
          properties: ['duration'],
        },
      },
    };

    expect(node.instance().isCompatible(report)).toBeTruthy();
  });

  it('should allow to combine a start date with an end date report', () => {
    const report1 = {
      id: '1',
      data: {
        processDefinitionKey: 'leadQualification',
        processDefinitionVersion: '1',
        view: {
          entity: 'flowNode',
          properties: ['duration'],
        },
        groupBy: {
          type: 'startDate',
          value: {unit: 'month'},
        },
        visualization: 'bar',
      },
    };
    const report2 = {
      id: '2',
      data: {
        processDefinitionKey: 'leadQualification',
        processDefinitionVersion: '2',
        view: {
          entity: 'flowNode',
          properties: ['duration'],
        },
        groupBy: {
          type: 'endDate',
          value: {unit: 'month'},
        },
        visualization: 'bar',
      },
    };
    const node = shallow(
      <CombinedReportPanel
        {...props}
        report={{
          id: 'combinedReport',
          name: 'Combined Report',
          combined: true,
          data: {
            configuration: {},
            reports: [{id: '1'}],
            visualization: 'bar',
          },
          result: {
            data: {
              1: report1,
            },
          },
        }}
      />
    );
    expect(node.instance().isCompatible(report2)).toBeTruthy();
  });

  it('should allow to combine a running date with an start date report', () => {
    const report1 = {
      id: '1',
      data: {
        processDefinitionKey: 'leadQualification',
        processDefinitionVersion: '1',
        view: {
          entity: 'processInstance',
          properties: ['frequency'],
        },
        groupBy: {
          type: 'runningDate',
          value: {unit: 'month'},
        },
        visualization: 'bar',
      },
    };
    const report2 = {
      id: '2',
      data: {
        processDefinitionKey: 'leadQualification',
        processDefinitionVersion: '2',
        view: {
          entity: 'processInstance',
          properties: ['frequency'],
        },
        groupBy: {
          type: 'startDate',
          value: {unit: 'month'},
        },
        visualization: 'bar',
      },
    };
    const node = shallow(
      <CombinedReportPanel
        {...props}
        report={{
          id: 'combinedReport',
          name: 'Combined Report',
          combined: true,
          data: {
            configuration: {},
            reports: [{id: '1'}],
            visualization: 'bar',
          },
          result: {
            data: {
              1: report1,
            },
          },
        }}
      />
    );
    expect(node.instance().isCompatible(report2)).toBeTruthy();
  });

  it('should not crash when dealing with variable reports', () => {
    const report1 = {
      id: '1',
      data: {
        processDefinitionKey: 'leadQualification',
        processDefinitionVersion: '1',
        view: {
          entity: 'processInstance',
          properties: ['frequency'],
        },
        groupBy: {
          type: 'none',
          value: null,
        },
        visualization: 'number',
      },
    };
    const report2 = {
      id: '2',
      data: {
        processDefinitionKey: 'leadQualification',
        processDefinitionVersion: '2',
        view: {
          entity: 'variable',
          properties: [{name: 'longVar', type: 'Long'}],
        },
        groupBy: {
          type: 'none',
          value: null,
        },
        visualization: 'number',
      },
    };
    const node = shallow(
      <CombinedReportPanel
        {...props}
        report={{
          id: 'combinedReport',
          name: 'Combined Report',
          combined: true,
          data: {
            configuration: {},
            reports: [{id: '1'}],
            visualization: 'bar',
          },
          result: {
            data: {
              1: report1,
            },
          },
        }}
      />
    );
    node.setState({reports: [report1, report2]});
    expect(node.instance().isCompatible(report2)).toBeFalsy();
  });
});

it('should update the color of a single report inside a combined report', async () => {
  const spy = jest.fn();
  const node = await shallow(
    <CombinedReportPanel
      {...props}
      report={{
        ...reportsList[1],
        data: {
          ...reportsList[1].data,
          reports: [
            {id: 'report1', color: 'red'},
            {id: 'report2', color: 'yellow'},
          ],
        },
      }}
      updateReport={spy}
    />
  );

  node.setState({selectedReports: [{id: 'report1'}, {id: 'report2'}]});

  node.instance().updateColor(1)('blue');

  expect(spy).toHaveBeenCalledWith({
    reports: {
      $set: [
        {color: 'red', id: 'report1'},
        {color: 'blue', id: 'report2'},
      ],
    },
  });
});

it('should generate new colors or preserve existing ones when selected/deselecting or reordering reports', async () => {
  const report = {
    id: 'combinedReport',
    name: 'Combined Report',
    combined: true,
    data: {
      configuration: {},
      reports: [
        {id: 'report1', color: 'red'},
        {id: 'report2', color: 'blue'},
        {id: 'report3', color: 'yellow'},
      ],
    },
    result: {
      data: {
        report1: {
          id: 'report1',
          ...singleReportData,
        },
        report2: {
          id: 'report2',
          ...singleReportData,
        },
        report3: {
          id: 'report3',
          ...singleReportData,
        },
      },
    },
  };

  const node = await shallow(<CombinedReportPanel {...props} report={report} />);
  node.setState({reports: Object.values(report.result.data)});
  const updatedColors = node.instance().getUpdatedColors([{id: 'report2'}, {id: 'report1'}]);

  expect(updatedColors).toEqual(['blue', 'red']);
});
