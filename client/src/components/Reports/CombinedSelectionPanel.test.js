import React from 'react';
import {mount} from 'enzyme';
import {loadEntity} from 'services';
import CombinedSelectionPanel from './CombinedSelectionPanel';

jest.mock('services', () => {
  return {
    loadEntity: jest.fn(),
    formatters: {
      getHighlightedText: text => text
    }
  };
});

jest.mock('components', () => {
  return {
    TypeaheadMultipleSelection: props => <ul>{JSON.stringify(props)}</ul>
  };
});

const reportsList = [
  {
    id: '10d5cd05-6f7c-4f74-970c-f31e065f646b',
    name: 'Single Report',
    reportType: 'single',
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
    reportType: 'combined',
    data: {
      configuration: {},
      reportIds: ['10d5cd05-6f7c-4f74-970c-f31e065f646b']
    }
  },
  {
    id: 'flow-node-report',
    name: 'heatmap report',
    reportType: 'single',
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
  const node = await mount(<CombinedSelectionPanel reportResult={reportsList[1]} />);
  await node.update();

  expect(loadEntity).toHaveBeenCalled();
});

it('should not include heatmap report', async () => {
  const node = await mount(<CombinedSelectionPanel reportResult={reportsList[1]} />);
  await node.update();

  expect(node).not.toIncludeText('heatmap');
});

it('should have input checkbox for only single report items in the list', async () => {
  const node = await mount(<CombinedSelectionPanel reportResult={reportsList[1]} />);
  await node.update();

  expect(node.find('ul').first()).toIncludeText('Single Report');
  expect(node.find('ul').first()).not.toIncludeText('Combined Report');
});

describe('isCompatible', () => {
  let node = {};
  beforeEach(async () => {
    node = await mount(<CombinedSelectionPanel reportResult={reportsList[1]} />);
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
