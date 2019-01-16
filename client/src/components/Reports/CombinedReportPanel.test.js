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

const reportsList = [
  {
    id: '10d5cd05-6f7c-4f74-970c-f31e065f646b',
    name: 'Single Report',
    combined: false,
    data: {
      processDefinitionKey: 'leadQualification',
      processDefinitionVersion: '1',
      view: {
        operation: 'median',
        entity: 'flowNode',
        property: 'duration'
      },
      groupBy: {
        type: 'flowNodes',
        value: null
      },
      visualization: 'bar'
    }
  },
  {
    id: '9100f0c9-4f8d-491d-b2ae-9d67d682a52d',
    name: 'Combined Report',
    combined: true,
    data: {
      configuration: {},
      reportIds: ['10d5cd05-6f7c-4f74-970c-f31e065f646b']
    },
    result: {
      '10d5cd05-6f7c-4f74-970c-f31e065f646b': {
        data: {
          visualization: ''
        }
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
        operation: 'median',
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
    <CombinedReportPanel updateReport={() => {}} configuration={{}} reportResult={reportsList[1]} />
  );
  await node.update();

  expect(loadEntity).toHaveBeenCalled();
});

it('should not include heatmap report', async () => {
  const node = await shallow(
    <CombinedReportPanel updateReport={() => {}} configuration={{}} reportResult={reportsList[1]} />
  );
  await node.update();

  expect(node).not.toIncludeText('heatmap');
});

it('should have input checkbox for only single report items in the list', async () => {
  const node = await shallow(
    <CombinedReportPanel updateReport={() => {}} configuration={{}} reportResult={reportsList[1]} />
  );
  await node.update();
  expect(JSON.stringify(node.find('TypeaheadMultipleSelection').props())).toMatch('Single Report');
  expect(JSON.stringify(node.find('TypeaheadMultipleSelection').props())).not.toMatch(
    'Combined Report'
  );
});

describe('isCompatible', () => {
  let node = {};
  beforeEach(async () => {
    node = await shallow(
      <CombinedReportPanel
        updateReport={() => {}}
        configuration={{}}
        reportResult={reportsList[1]}
      />
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
          operation: 'avg',
          entity: 'flowNode',
          property: 'duration'
        }
      }
    };

    expect(node.instance().isCompatible(reportSameOperation)).toBeTruthy();
  });

  it('should only allow to combine if both reports has the same view entity and proberty', async () => {
    const reportSameOperation = {
      ...reportsList[0],
      data: {
        ...reportsList[0].data,
        view: {
          operation: 'median',
          entity: 'process instance',
          property: 'frequency'
        }
      }
    };

    expect(node.instance().isCompatible(reportSameOperation)).toBeFalsy();
  });
});

xit('should update the color of a single report inside a combined report', async () => {
  const spy = jest.fn();
  const node = await shallow(
    <CombinedReportPanel
      reportResult={reportsList[1]}
      configuration={{color: ['red', 'yellow']}}
      updateReport={spy}
    />
  );

  node.setState({selectedReports: [{id: 'report1'}, {id: 'report2'}]});

  node.instance().updateColor(1)('blue');

  expect(spy).toHaveBeenCalledWith({configuration: {color: ['red', 'blue']}});
});

it('should generate new colors or preserve existing ones when selected/deselecting or reordering reports', async () => {
  const node = await shallow(
    <CombinedReportPanel reportResult={reportsList[1]} configuration={{color: ['red', 'yellow']}} />
  );

  node.setState({selectedReports: [{id: 'report1'}, {id: 'report2'}, {id: 'report3'}]});

  const updateColors = node.instance().getUpdatedColors([{id: 'report2'}, {id: 'report1'}]);

  expect(updateColors).toEqual(['yellow', 'red', '#00d0a3']);
});

xit('should invok updateReport on mount when combined report has reports but these reports has no defined colors', async () => {
  const spy = jest.fn();
  await shallow(
    <CombinedReportPanel
      updateReport={spy}
      reportResult={reportsList[1]}
      configuration={{color: undefined}}
    />
  );

  expect(spy).toHaveBeenCalledWith({configuration: {color: ['#1991c8']}});
});
