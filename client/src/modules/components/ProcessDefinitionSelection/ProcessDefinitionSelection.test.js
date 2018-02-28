import React from 'react';
import {mount} from 'enzyme';

import ProcessDefinitionSelection from './ProcessDefinitionSelection';

import {loadProcessDefinitions} from './service';

jest.mock('components', () => {
  const Select = props => <select id="selection" {...props}>{props.children}</select>;
  Select.Option = props => <option {...props}>{props.children}</option>;

  return {
    Select,
    BPMNDiagram: props => <div id='diagram'>Diagram {props.children} {props.xml}</div>
  };
});

jest.mock('./service', () => {return {
  loadProcessDefinitions: jest.fn()
}});

const spy = jest.fn();

const props = {
  onChange: spy
}

loadProcessDefinitions.mockReturnValue([
  {key:'foo', 
    versions: [
      {id:'procdef2', key: 'foo', version: 2},      
      {id:'procdef1', key: 'foo', version: 1}
    ]
  },
  {key:'bar', 
    versions: [
      {id:'anotherProcDef', key: 'bar', version: 1}
    ]
  }

]);

it('should render without crashing', () => {
  mount(<ProcessDefinitionSelection {...props}/>);
});

it('should display a loading indicator', () => {
  const node = mount(<ProcessDefinitionSelection {...props}/>);

  expect(node.find('.ProcessDefinitionSelection__loading-indicator')).toBePresent();
});

it('should initially load all process definitions', () => {
  mount(<ProcessDefinitionSelection {...props}/>);

  expect(loadProcessDefinitions).toHaveBeenCalled();
});

it('should update to most recent version when key is selected', async () => {
  spy.mockClear();
  const node = await mount(<ProcessDefinitionSelection {...props} />);
  await node.update();

  await node.instance().changeKey({target: {value:'foo'}});

  expect(spy.mock.calls[0][0].processDefinitionVersion).toBe(2);
});


it('should update definition if versions is changed', async () => {
  spy.mockClear();
  const node = await mount(<ProcessDefinitionSelection processDefinitionKey='foo' {...props}/>);
  await node.update();

  await node.instance().changeVersion({target: {value:'1'}});

  expect(spy.mock.calls[0][0].processDefinitionVersion).toBe('1');
});

it('should set key and version, if process definition is already available', async () => {
  spy.mockClear();
  const definitionConfig = {
    processDefinitionKey: 'foo',
    processDefinitionVersion: 2
  };
  const node = await mount(<ProcessDefinitionSelection {...definitionConfig} {...props} />);
  await node.update();

  expect(node).toIncludeText('foo');
  expect(node).toIncludeText(2);    
});

it('should call onChange function on change of the definition', async () => {
  spy.mockClear();
  const definitionConfig = {
    processDefinitionKey: 'foo',
    processDefinitionVersion: 2
  };
  const node = await mount(<ProcessDefinitionSelection {...definitionConfig} {...props} />);
  await node.update();

  await node.instance().changeVersion({target: {value:'1'}});

  expect(spy).toHaveBeenCalled();
});

it('should render diagram if enabled and definition is selected', async () => {
  const definitionConfig = {
    processDefinitionKey: 'foo',
    processDefinitionVersion: 2
  };
  const node = await mount(<ProcessDefinitionSelection renderDiagram={true} {...definitionConfig} {...props} />);
  await node.update();


  expect(node).toIncludeText('Diagram');
});

it('should disable version selection, if no key is selected', async () => {
  const node = await mount(<ProcessDefinitionSelection {...props} />);
  await node.update();

  const versionSelect= node.find('select[name="ProcessDefinitionSelection__version"]');
  expect(versionSelect.prop("disabled")).toBeTruthy();
});

it('should display all option in version selection if enabled', async () => {
  const node = await mount(<ProcessDefinitionSelection enableAllVersionSelection={true} {...props} />);
  await node.update();

  expect(node.find('option[value="ALL"]').text()).toBe('all');
});

it('should not display all option in version selection if disabled', async () => {
  const node = await mount(<ProcessDefinitionSelection enableAllVersionSelection={false} {...props} />);
  await node.update();

  expect(node.find('option[value="ALL"]').exists()).toBe(false);
});