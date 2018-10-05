import React from 'react';
import {mount} from 'enzyme';

import ReportControlPanel from './ReportControlPanel';
import {extractProcessDefinitionName, reportConfig, getFlowNodeNames} from 'services';

import {loadVariables} from './service';

const flushPromises = () => new Promise(resolve => setImmediate(resolve));

jest.mock('./filter', () => {
  return {
    Filter: () => 'Filter'
  };
});

jest.mock('components', () => {
  const Dropdown = props => <div {...props}>{props.children}</div>;
  Dropdown.Option = props => <button {...props}>{props.children}</button>;
  Dropdown.Submenu = ({onOpen, ...props}) => <div {...props} />;

  return {
    Dropdown,
    Popover: ({title, children}) => (
      <div>
        {title} {children}
      </div>
    ),
    Labeled: props => (
      <div>
        <label id={props.id}>{props.label}</label>
        {props.children}
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
    },
    getFlowNodeNames: jest.fn().mockReturnValue({
      a: 'foo',
      b: 'bar'
    })
  };
});

jest.mock('./ProcessPart', () => {
  return {ProcessPart: () => <div>ProcessPart</div>};
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

  expect(node.find('.configDropdown').at(2)).toBeDisabled();
  expect(node.find('.configDropdown').at(3)).toBeDisabled();
});

it('should not disable the groupBy and visualization Selects if view is selected', () => {
  const node = mount(<ReportControlPanel {...data} />);

  expect(node.find('.configDropdown').at(2)).not.toBeDisabled();
  expect(node.find('.configDropdown').at(3)).not.toBeDisabled();
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

  expect(node.find('.processDefinitionPopover')).toIncludeText('aName');
});

it('should change process definition name if process definition is updated', async () => {
  const node = await mount(<ReportControlPanel {...data} />);

  extractProcessDefinitionName.mockReturnValue('aName');
  node.setProps({processDefinitionKey: 'bar'});

  expect(node.find('.processDefinitionPopover')).toIncludeText('aName');
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

it('should load the flownode names and hand them to the filter and process part', async () => {
  const node = mount(
    <ReportControlPanel {...data} view={{entity: 'processInstance', property: 'duration'}} />
  );

  await flushPromises();
  node.update();

  expect(getFlowNodeNames).toHaveBeenCalled();
  expect(node.find('Filter').prop('flowNodeNames')).toEqual(getFlowNodeNames());
  expect(node.find('ProcessPart').prop('flowNodeNames')).toEqual(getFlowNodeNames());
});

it('should only display process part button if view is process instance duration', () => {
  const node = mount(
    <ReportControlPanel {...data} view={{entity: 'processInstance', property: 'duration'}} />
  );

  expect(node.find('ProcessPart')).toBePresent();

  node.setProps({view: {entity: 'processInstance', property: 'frequency'}});

  expect(node.find('ProcessPart')).not.toBePresent();
});

it('should not update the target value when changing from line chart to barchart or the reverse', () => {
  const spy = jest.fn();
  const node = mount(
    <ReportControlPanel
      {...data}
      visualization="bar"
      view={{entity: 'processInstance', property: 'duration'}}
      updateReport={spy}
    />
  );

  node.instance().update('visualization', 'line');
  expect(spy).toHaveBeenCalledWith({
    groupBy: null,
    visualization: null
  });
});

it('should reset the target value when changing from line chart or barchart to something else', () => {
  const spy = jest.fn();
  const node = mount(
    <ReportControlPanel
      {...data}
      visualization="bar"
      view={{entity: 'processInstance', property: 'duration'}}
      updateReport={spy}
    />
  );

  node.instance().update('visualization', 'something else');
  expect(spy).toHaveBeenCalledWith({
    configuration: {targetValue: {active: false}, xml: 'fooXml'},
    groupBy: null,
    visualization: null
  });
});
