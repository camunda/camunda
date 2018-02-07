import React from 'react';
import {mount} from 'enzyme';

import ControlPanel from './ControlPanel';

jest.mock('./service', () => {return {
  loadProcessDefinitions: () => [{key:'foo', versions: [{id:'procdef1'}, {id:'procdef2'}]}]
}});
jest.mock('./filter', () => {return {
  Filter: () => 'Filter'
}});

jest.mock('components', () => {
  const Select = props => <select {...props}>{props.children}</select>;
  Select.Option = props => <option {...props}>{props.children}</option>;

  return {Select,
    Popover: ({children}) => children
  };
});

jest.mock('services', () => {return {
  reportLabelMap: {
    objectToLabel: () => 'foo',
    objectToKey: () => 'foo',
    keyToLabel: () => 'foo',
    getOptions: () => [],
    keyToObject: () => 'foo',
  }
}});

const data = {
  processDefinitionId: '',
  view: {operation: 'count', entity: 'processInstance'},
  groupBy: {type: 'none', unit: null},
  visualization: 'json',
  filter: null
};

const spy = jest.fn();

it('should display available process definitions', async () => {
  const node = mount(<ControlPanel {...data} onChange={spy} />);

  await node.instance().loadAvailableDefinitions();

  expect(node).toIncludeText('procdef1');
  expect(node).toIncludeText('procdef2');
});

it('should call the provided onChange property function when a setting changes', () => {
  const node = mount(<ControlPanel {...data} onChange={spy} />);

  node.instance().changeVisualization({target: {value: 'someTestVis'}});

  expect(spy).toHaveBeenCalledWith('visualization', 'someTestVis');
});
