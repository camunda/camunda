/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {loadEntity} from 'services';
import CombinedReportPanel from './CombinedReportPanel';

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    formatters: {
      getHighlightedText: text => text
    },
    loadEntity: jest.fn()
  };
});

const singleReportData = {
  processDefinitionKey: 'leadQualification',
  processDefinitionVersion: '1',
  view: {
    entity: 'flowNode',
    property: 'duration'
  },
  groupBy: {
    type: 'flowNodes',
    value: null
  },
  visualization: 'bar'
};

const reportsList = [
  {
    id: 'singleReport',
    name: 'Single Report',
    combined: false,
    data: singleReportData
  },
  {
    id: 'combinedReport',
    name: 'Combined Report',
    combined: true,
    data: {
      configuration: {},
      reports: [{id: 'singleReport'}],
      visualization: 'bar'
    },
    result: {
      singleReport: {
        id: 'singleReport',
        data: singleReportData
      }
    }
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
        property: 'duration'
      },
      groupBy: {
        type: 'flowNodes',
        value: null
      },
      visualization: 'heat'
    }
  }
];

loadEntity.mockReturnValue(reportsList);

it('should invoke loadEntity to load all reports when it is mounted', async () => {
  const node = await shallow(
    <CombinedReportPanel updateReport={() => {}} configuration={{}} report={reportsList[1]} />
  );
  await node.update();

  expect(loadEntity).toHaveBeenCalled();
});

it('should not include heatmap report', async () => {
  const node = await shallow(
    <CombinedReportPanel updateReport={() => {}} configuration={{}} report={reportsList[1]} />
  );
  await node.update();

  expect(node).not.toIncludeText('heatmap');
});

it('should have input checkbox for only single report items in the list', async () => {
  const node = await shallow(
    <CombinedReportPanel updateReport={() => {}} report={reportsList[1]} />
  );
  await node.update();
  expect(JSON.stringify(node.find('TypeaheadMultipleSelection').props())).toMatch('singleReport');
  expect(JSON.stringify(node.find('TypeaheadMultipleSelection').props())).not.toMatch(
    'Combined Report'
  );
});

describe('isCompatible', () => {
  let node = {};
  beforeEach(async () => {
    node = await shallow(
      <CombinedReportPanel updateReport={() => {}} configuration={{}} report={reportsList[1]} />
    );
    await node.update();
  });

  it('should only allow to combine if both reports has the same visualization', async () => {
    const tableReport = {
      ...reportsList[0],
      data: {
        ...reportsList[0].data,
        visualization: 'table'
      }
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
          value: null
        }
      }
    };

    expect(node.instance().isCompatible(groupByNone)).toBeFalsy();
  });

  it('should only allow to combine if both reports has the different view operation', async () => {
    const reportSameOperation = {
      ...reportsList[0],
      data: {
        ...reportsList[0].data,
        view: {
          entity: 'flowNode',
          property: 'duration'
        }
      }
    };

    expect(node.instance().isCompatible(reportSameOperation)).toBeTruthy();
  });

  it('should only allow to combine if both reports has the same view entity and property', async () => {
    const reportSameOperation = {
      ...reportsList[0],
      data: {
        ...reportsList[0].data,
        view: {
          entity: 'process instance',
          property: 'frequency'
        }
      }
    };

    expect(node.instance().isCompatible(reportSameOperation)).toBeFalsy();
  });

  it('should allow to combined a userTask duration report with a flowNode duration report', async () => {
    const report = {
      ...reportsList[0],
      data: {
        ...reportsList[0].data,
        view: {
          entity: 'userTask',
          property: 'workDuration'
        }
      }
    };

    expect(node.instance().isCompatible(report)).toBeTruthy();
  });
});

it('should update the color of a single report inside a combined report', async () => {
  const spy = jest.fn();
  const node = await shallow(
    <CombinedReportPanel
      report={{
        ...reportsList[1],
        data: {
          ...reportsList[1].data,
          reports: [{id: 'report1', color: 'red'}, {id: 'report2', color: 'yellow'}]
        }
      }}
      updateReport={spy}
    />
  );

  node.setState({selectedReports: [{id: 'report1'}, {id: 'report2'}]});

  node.instance().updateColor(1)('blue');

  expect(spy).toHaveBeenCalledWith({
    reports: {$set: [{color: 'red', id: 'report1'}, {color: 'blue', id: 'report2'}]}
  });
});

it('should generate new colors or preserve existing ones when selected/deselecting or reordering reports', async () => {
  const reportData = {data: {visualization: ''}};
  const report = {
    id: 'combinedReport',
    name: 'Combined Report',
    combined: true,
    data: {
      configuration: {},
      reports: [
        {id: 'report1', color: 'red'},
        {id: 'report2', color: 'blue'},
        {id: 'report3', color: 'yellow'}
      ]
    }
  };
  const result = {
    report1: {
      id: 'report1',
      ...reportData
    },
    report2: {
      id: 'report2',
      ...reportData
    },
    report3: {
      id: 'report3',
      ...reportData
    }
  };

  const node = await shallow(
    <CombinedReportPanel
      report={{
        ...report,
        result
      }}
    />
  );
  const updatedColors = node.instance().getUpdatedColors([{id: 'report2'}, {id: 'report1'}]);

  expect(updatedColors).toEqual(['blue', 'red']);
});
