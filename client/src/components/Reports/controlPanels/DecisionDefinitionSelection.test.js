import React from 'react';
import {shallow} from 'enzyme';

import DecisionDefinitionSelection from './DecisionDefinitionSelection';

import {loadDecisionDefinitions} from 'services';

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    loadDecisionDefinitions: jest.fn()
  };
});

loadDecisionDefinitions.mockReturnValue([
  {
    key: 'foo',
    versions: [
      {id: 'decisiondef2', key: 'foo', version: 2},
      {id: 'decisiondef1', key: 'foo', version: 1}
    ]
  },
  {
    key: 'bar',
    versions: [{id: 'anotherDecisionDef', key: 'bar', version: 1}]
  }
]);

it('should display a loading indicator', () => {
  const node = shallow(<DecisionDefinitionSelection />);

  expect(node.find('LoadingIndicator')).toBePresent();
});

it('should initially load all process definitions', () => {
  shallow(<DecisionDefinitionSelection />);

  expect(loadDecisionDefinitions).toHaveBeenCalled();
});

it('should update to most recent version when key is selected', async () => {
  const spy = jest.fn();

  const node = await shallow(<DecisionDefinitionSelection onChange={spy} />);

  node.instance().changeKey({target: {value: 'foo'}});

  expect(spy).toHaveBeenCalledWith('foo', 2);
});

it('should update definition if versions is changed', async () => {
  const spy = jest.fn();

  const node = await shallow(
    <DecisionDefinitionSelection decisionDefinitionKey="foo" onChange={spy} />
  );

  node.instance().changeVersion({target: {value: 1}});

  expect(spy).toHaveBeenCalledWith('foo', 1);
});

it('should set key and version, if process definition is already available', async () => {
  const definitionConfig = {
    decisionDefinitionKey: 'foo',
    decisionDefinitionVersion: 2
  };
  const node = await shallow(<DecisionDefinitionSelection {...definitionConfig} />);

  expect(
    node
      .find('Select')
      .at(0)
      .prop('value')
  ).toBe('foo');
  expect(
    node
      .find('Select')
      .at(1)
      .prop('value')
  ).toBe(2);
});

it('should disable version selection, if no key is selected', async () => {
  const node = await shallow(<DecisionDefinitionSelection />);

  expect(node.find('.ProcessDefinitionSelection__version-select')).toBeDisabled();
});

it('should display all option in version selection', async () => {
  const node = await shallow(<DecisionDefinitionSelection />);

  expect(node.find('.ProcessDefinitionSelection__version-select [value="ALL"]')).toBePresent();
});

it('should show a note if the selected ProcDef version is ALL', async () => {
  const node = await shallow(
    <DecisionDefinitionSelection decisionDefinitionKey="foo" decisionDefinitionVersion="ALL" />
  );

  expect(node.find('.ProcessDefinitionSelection__version-select__warning')).toBePresent();
});
