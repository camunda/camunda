import React from 'react';
import {mount} from 'enzyme';

import ControlPanel from './ControlPanel';

jest.mock('./filter', () => {return {
  Filter: () => 'Filter'
}});

jest.mock('./service', () => {return {
  loadProcessDefinitions: () => [{key:'foo', versions: [{id:'procdef1'}, {id:'procdef2'}]}]
}});

jest.mock('components', () => {
  const Select = props => <select {...props}>{props.children}</select>;
  Select.Option = props => <option {...props}>{props.children}</option>;

  return {Select,
    Popover: ({children}) => children,
    ProcessDefinitionSelection: (props) => <div>ProcessDefinitionSelection</div>
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
  filter: null,
  configuration: {xml: 'fooXml'}
};

const spy = jest.fn();

it('should call the provided onChange property function when a setting changes', () => {
  const node = mount(<ControlPanel {...data} onChange={spy} />);

  node.instance().changeVisualization({target: {value: 'someTestVis'}});

  expect(spy).toHaveBeenCalledWith({'visualization': 'someTestVis'});
});
