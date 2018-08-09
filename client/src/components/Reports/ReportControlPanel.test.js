import React from 'react';
import {mount} from 'enzyme';

import ReportControlPanel from './ReportControlPanel';
import {extractProcessDefinitionName, reportConfig} from 'services';

import {loadVariables} from './service';

jest.mock('./filter', () => {
  return {
    Filter: () => 'Filter'
  };
});

jest.mock('components', () => {
  const Dropdown = props => <div {...props}>{props.children}</div>;
  Dropdown.Option = props => <button {...props}>{props.children}</button>;
  Dropdown.Submenu = props => <div {...props} />;

  return {
    Dropdown,
    Popover: ({title, children}) => (
      <div>
        {title} {children}
      </div>
    ),
    ProcessDefinitionSelection: props => <div>ProcessDefinitionSelection</div>,
    Button: props => <button {...props} />,
    Input: props => <input {...props} />
  };
});

jest.mock('services', () => {
  return {
    reportConfig: {
      getLabelFor: () => 'foo',
      view: {foo: {data: 'foo', label: 'viewfoo'}},
      groupBy: {
        foo: {data: 'foo', label: 'groupbyfoo'},
        variable: {data: {value: []}, label: 'Variables'}
      },
      visualization: {foo: {data: 'foo', label: 'visualizationfoo'}},
      isAllowed: jest.fn().mockReturnValue(true),
      getNext: jest.fn()
    },
    extractProcessDefinitionName: jest.fn(),
    formatters: {
      getHighlightedText: text => text
    }
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

it('should call the provided updateReport property function when a setting changes', () => {
  const node = mount(<ReportControlPanel {...data} updateReport={spy} />);

  node.instance().update('visualization', 'someTestVis');

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0].visualization).toBe('someTestVis');
});

it('should toggle target value view mode off when a setting changes', () => {
  const node = mount(<ReportControlPanel {...data} updateReport={spy} />);

  node.instance().update('visualization', 'someTestVis');

  expect(spy.mock.calls[0][0].configuration.targetValue.active).toBe(false);
});

it('should disable the groupBy and visualization Selects if view is not selected', () => {
  const node = mount(<ReportControlPanel {...data} view="" />);

  expect(node.find('.ReportControlPanel__dropdown').at(2)).toBeDisabled();
  expect(node.find('.ReportControlPanel__dropdown').at(3)).toBeDisabled();
});

it('should not disable the groupBy and visualization Selects if view is selected', () => {
  const node = mount(<ReportControlPanel {...data} />);

  expect(node.find('.ReportControlPanel__select').at(2)).not.toBeDisabled();
  expect(node.find('.ReportControlPanel__select').at(3)).not.toBeDisabled();
});

it('should set or reset following selects according to the getNext function', () => {
  const node = mount(<ReportControlPanel {...data} updateReport={spy} />);

  reportConfig.getNext.mockReturnValueOnce('next');
  node.instance().update('view', 'foo');

  expect(spy).toHaveBeenCalledWith({
    configuration: {targetValue: {active: false}, xml: 'fooXml'},
    view: 'foo',
    groupBy: 'next'
  });
});

it('should disable options, which would create wrong combination', () => {
  reportConfig.isAllowed.mockReturnValue(false);
  const node = mount(<ReportControlPanel {...data} onChange={spy} />);
  node.setProps({view: 'baz'});

  expect(
    node
      .find('Dropdown')
      .at(1)
      .find('button')
  ).toBeDisabled();
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

it('should only include variables that match the typeahead', () => {
  const node = mount(<ReportControlPanel {...data} />);

  node.setState({
    variables: [{name: 'Foo'}, {name: 'Bar'}, {name: 'Foobar'}],
    variableTypeaheadValue: 'foo'
  });

  expect(node).toIncludeText('Foo');
  expect(node).toIncludeText('Foobar');
  expect(node).not.toIncludeText('Bar');
});

it('should show pagination for many variables', () => {
  const node = mount(<ReportControlPanel {...data} />);

  node.setState({
    variables: [
      {name: 'varA'},
      {name: 'varB'},
      {name: 'varC'},
      {name: 'varD'},
      {name: 'varE'},
      {name: 'varF'},
      {name: 'varG'}
    ]
  });

  expect(node).toIncludeText('varA');
  expect(node).toIncludeText('varB');
  expect(node).toIncludeText('varC');
  expect(node).toIncludeText('varD');
  expect(node).toIncludeText('varE');
  expect(node).not.toIncludeText('varF');
  expect(node).not.toIncludeText('varG');
  expect(node).toIncludeText('2 more items');
  expect(node).toIncludeText('Load More');
});

it('should show an "Always show tooltips" button for heatmaps', () => {
  const node = mount(<ReportControlPanel {...data} visualization="heat" />);

  expect(node).toIncludeText('Always show tooltips');
});

it('should not show an "Always show tooltips" button for other visualizations', () => {
  const node = mount(<ReportControlPanel {...data} visualization="something" />);

  expect(node).not.toIncludeText('Always show tooltips');
});
