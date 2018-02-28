import React from 'react';
import {mount} from 'enzyme';

import ControlPanel from './ControlPanel';

jest.mock('./filter', () => {return {
  Filter: () => 'Filter'
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
    getOptions: () => [{key: 'foo',label: 'foo',allowedNext:['not_foo']}],
    keyToObject: (key) => key,
  }
}});

jest.mock('./targetValue', () => {return {TargetValueComparison: () => <div>TargetValueComparison</div>}});

const data = {
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

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0].visualization).toBe('someTestVis');
});

it('should toggle target value view mode off when a setting changes', () => {
  const node = mount(<ControlPanel {...data} onChange={spy} />);

  node.instance().changeVisualization({target: {value: 'someTestVis'}});

  expect(spy.mock.calls[0][0].configuration.targetValue.active).toBe(false);
});

it('should disable the groupBy and visualizeAs Selects if view is not selected', () => {
  const node = mount(<ControlPanel {...data} view=''/>);

  expect(node.find('.ControlPanel__select').at(2)).toBeDisabled();
  expect(node.find('.ControlPanel__select').at(3)).toBeDisabled();
});

it('should not disable the groupBy and visualizeAs Selects if view is selected', () => {
  const node = mount(<ControlPanel {...data}/>);

  expect(node.find('.ControlPanel__select').at(2)).not.toBeDisabled();
  expect(node.find('.ControlPanel__select').at(3)).not.toBeDisabled();
});

it('should reset the next Selects if in conflict with previous one', () => {
  const node = mount(<ControlPanel {...data} onChange={spy} />);
  node.setProps({view: 'foo'});

  expect(spy).toHaveBeenCalledWith({'groupBy': '', 'visualization': ''});
});
