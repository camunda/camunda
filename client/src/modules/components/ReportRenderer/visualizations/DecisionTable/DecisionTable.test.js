import React from 'react';

import DecisionTable from './DecisionTable';
import Viewer from 'dmn-js';

import {shallow} from 'enzyme';

jest.mock('dmn-js', () =>
  jest.fn().mockImplementation(() => ({
    importXML: jest.fn().mockImplementation((xml, callback) => callback()),
    open: jest.fn(),
    getViews: jest
      .fn()
      .mockReturnValue([
        {type: 'decisionTable', element: {id: 'a'}},
        {type: 'decisionTable', element: {id: 'key'}},
        {type: 'decisionTable', element: {id: 'c'}}
      ])
  }))
);

const props = {
  report: {
    data: {
      configuration: {
        xml: 'dmn xml string'
      },
      decisionDefinitionKey: 'key'
    },
    decisionInstanceCount: 3,
    result: {
      a: 1,
      b: 2
    }
  }
};

it('should construct a new Viewer instance', () => {
  shallow(<DecisionTable {...props} />);

  expect(Viewer).toHaveBeenCalled();
});

it('should import the provided xml', () => {
  const node = shallow(<DecisionTable {...props} />);

  expect(node.instance().viewer.importXML).toHaveBeenCalled();
  expect(node.instance().viewer.importXML.mock.calls[0][0]).toBe('dmn xml string');
});

it('should open the view of the appropriate decision table', () => {
  const node = shallow(<DecisionTable {...props} />);

  expect(node.instance().viewer.open).toHaveBeenCalled();
  expect(node.instance().viewer.open.mock.calls[0][0]).toEqual({
    type: 'decisionTable',
    element: {id: 'key'}
  });
});

it('should render content in DmnJsPortals', () => {
  const node = shallow(<DecisionTable {...props} />);

  node.setState({
    entryPoints: {
      rules: {
        a: 'htmlA',
        b: 'htmlB'
      },
      summary: 'htmlSum'
    }
  });

  expect(node).toMatchSnapshot();
});

it('should display meaningful data if there are no evaluations', () => {
  const node = shallow(
    <DecisionTable report={{data: props.report.data, decisionInstanceCount: 0, result: {}}} />
  );

  node.setState({
    entryPoints: {
      rules: {
        a: 'htmlA',
        b: 'htmlB'
      },
      summary: 'htmlSum'
    }
  });

  expect(node).toMatchSnapshot();
});
