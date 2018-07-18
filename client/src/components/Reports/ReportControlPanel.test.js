import React from 'react';
import {mount} from 'enzyme';

import ReportControlPanel from './ReportControlPanel';
import {extractProcessDefinitionName} from 'services';

import {loadVariables} from './service';

jest.mock('./filter', () => {
  return {
    Filter: () => 'Filter'
  };
});

jest.mock('components', () => {
  const Select = props => <select {...props}>{props.children}</select>;
  Select.Option = props => <option {...props}>{props.children}</option>;

  return {
    Select,
    Popover: ({title, children}) => (
      <div>
        {title} {children}
      </div>
    ),
    ProcessDefinitionSelection: props => <div>ProcessDefinitionSelection</div>
  };
});

jest.mock('services', () => {
  return {
    reportLabelMap: {
      objectToLabel: () => 'foo',
      objectToKey: () => '' + Math.random(),
      keyToLabel: () => 'foo',
      getOptions: type => [
        {key: type + 'foo', label: type + 'foo'},
        {key: type + 'bar', label: type + 'bar'}
      ],
      keyToObject: key => key,
      getEnabledOptions: type => [type + 'foo'],
      getTheRightCombination: () => {
        return {view: 'foo', groupBy: 'theRightGroupBy', visualization: 'theRightViz'};
      }
    },
    extractProcessDefinitionName: jest.fn()
  };
});

jest.mock('./targetValue', () => {
  return {TargetValueComparison: () => <div>TargetValueComparison</div>};
});

jest.mock('./service', () => {
  return {
    loadVariables: jest.fn().mockReturnValue([])
  };
});

const data = {
  processDefinitionKey: 'aKey',
  processDefinitionVersion: 'aVersion',
  view: {operation: 'count', entity: 'processInstance'},
  groupBy: {type: 'none', unit: null},
  visualization: 'number',
  filter: null,
  configuration: {xml: 'fooXml'}
};

extractProcessDefinitionName.mockReturnValue('foo');
const spy = jest.fn();

it('should call the provided onChange property function when a setting changes', () => {
  const node = mount(<ReportControlPanel {...data} onChange={spy} />);

  node.instance().changeVisualization({target: {value: 'someTestVis'}});

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0].visualization).toBe('someTestVis');
});

it('should toggle target value view mode off when a setting changes', () => {
  const node = mount(<ReportControlPanel {...data} onChange={spy} />);

  node.instance().changeVisualization({target: {value: 'someTestVis'}});

  expect(spy.mock.calls[0][0].configuration.targetValue.active).toBe(false);
});

it('should disable the groupBy and visualization Selects if view is not selected', () => {
  const node = mount(<ReportControlPanel {...data} view="" />);

  expect(node.find('.ReportControlPanel__select').at(2)).toBeDisabled();
  expect(node.find('.ReportControlPanel__select').at(3)).toBeDisabled();
});

it('should not disable the groupBy and visualization Selects if view is selected', () => {
  const node = mount(<ReportControlPanel {...data} />);

  expect(node.find('.ReportControlPanel__select').at(2)).not.toBeDisabled();
  expect(node.find('.ReportControlPanel__select').at(3)).not.toBeDisabled();
});

it('should set or reset following selects according to getTheRightCombination function', () => {
  const node = mount(<ReportControlPanel {...data} onChange={spy} />);
  node.instance().changeView({target: {value: 'foo'}});

  expect(spy).toHaveBeenCalledWith({
    configuration: {targetValue: {active: false}, xml: 'fooXml'},
    view: 'foo',
    groupBy: 'theRightGroupBy',
    visualization: 'theRightViz'
  });
});

it('should disable options, which would create wrong combination', () => {
  const node = mount(<ReportControlPanel {...data} onChange={spy} />);
  node.setProps({view: 'baz'});

  expect(node.find('[value="groupBybar"]').first()).toBeDisabled();
});

it('should show process definition name', async () => {
  extractProcessDefinitionName.mockReturnValue('aName');

  const node = await mount(<ReportControlPanel {...data} />);

  expect(node.find('.ReportControlPanel__popover')).toIncludeText('aName');
});

it('should change process definition name if process definition is updated', async () => {
  const node = await mount(<ReportControlPanel {...data} />);

  extractProcessDefinitionName.mockReturnValue('aName');
  node.setProps({processDefinitionKey: 'bar'});

  expect(node.find('.ReportControlPanel__popover')).toIncludeText('aName');
});

it('should load the variables of the process', () => {
  const node = mount(<ReportControlPanel {...data} />);

  node.setProps({processDefinitionKey: 'bar', processDefinitionVersion: 'ALL'});

  expect(loadVariables).toHaveBeenCalledWith('bar', 'ALL');
});

it('should include variables in the groupby options', () => {
  const node = mount(<ReportControlPanel {...data} />);

  node.setState({variables: [{name: 'Var1'}, {name: 'Var2'}]});

  expect(node).toIncludeText('Var1');
  expect(node).toIncludeText('Var2');
});
