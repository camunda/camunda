/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {LoadingIndicator, Dropdown} from 'components';

import DefinitionSelection from './DefinitionSelection';

import {loadDefinitions} from 'services';

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    loadDefinitions: jest.fn().mockReturnValue([
      {
        key: 'foo',
        versions: [
          {id: 'procdef2', key: 'foo', version: '2'},
          {id: 'procdef1', key: 'foo', version: '1'}
        ]
      },
      {
        key: 'bar',
        versions: [{id: 'anotherProcDef', key: 'bar', version: '1'}]
      }
    ])
  };
});

const spy = jest.fn();

const props = {
  type: 'process',
  onChange: spy
};

it('should render without crashing', () => {
  shallow(<DefinitionSelection {...props} />);
});

it('should display a loading indicator', () => {
  const node = shallow(<DefinitionSelection {...props} />);

  expect(node.find(LoadingIndicator)).toBePresent();
});

it('should initially load all definitions', () => {
  shallow(<DefinitionSelection {...props} />);

  expect(loadDefinitions).toHaveBeenCalled();
});

it('should update to most recent version when key is selected', async () => {
  spy.mockClear();
  const node = await shallow(<DefinitionSelection {...props} />);

  await node.instance().changeKey('foo');

  expect(spy.mock.calls[0][1]).toBe('2');
});

it('should update definition if versions is changed', async () => {
  spy.mockClear();
  const node = await shallow(<DefinitionSelection definitionKey="foo" {...props} />);

  await node.instance().changeVersion('1');

  expect(spy.mock.calls[0][1]).toBe('1');
});

it('should set key and version, if process definition is already available', async () => {
  const definitionConfig = {
    definitionKey: 'foo',
    definitionVersion: '2'
  };
  const node = await shallow(<DefinitionSelection {...definitionConfig} {...props} />);

  expect(node.find('.name')).toHaveProp('label', 'foo');
  expect(node.find('.version')).toHaveProp('label', '2');
});

it('should call onChange function on change of the definition', async () => {
  spy.mockClear();
  const definitionConfig = {
    definitionKey: 'foo',
    definitionVersion: '2'
  };
  const node = await shallow(<DefinitionSelection {...definitionConfig} {...props} />);

  await node.instance().changeVersion('1');

  expect(spy).toHaveBeenCalled();
});

it('should render diagram if enabled and definition is selected', async () => {
  const definitionConfig = {
    definitionKey: 'foo',
    definitionVersion: '2'
  };
  const node = await shallow(
    <DefinitionSelection renderDiagram {...definitionConfig} {...props} />
  );

  expect(node).toIncludeText('Diagram');
});

it('should disable version selection, if no key is selected', async () => {
  const node = await shallow(<DefinitionSelection {...props} />);

  const versionSelect = node.find('.version');
  expect(versionSelect.prop('disabled')).toBeTruthy();
});

it('should display all option in version selection if enabled', async () => {
  const node = await shallow(<DefinitionSelection enableAllVersionSelection {...props} />);

  expect(
    node
      .find(Dropdown)
      .last()
      .childAt(0)
      .text()
  ).toBe('all');
});

it('should not display all option in version selection if disabled', async () => {
  const node = await shallow(<DefinitionSelection {...props} />);

  expect(
    node
      .find(Dropdown)
      .last()
      .children()
  ).toHaveLength(0);
});

it('should show a note if the selected ProcDef version is ALL', async () => {
  const node = await shallow(
    <DefinitionSelection enableAllVersionSelection definitionVersion="ALL" />
  );

  expect(node.find('.warning')).toBePresent();
});
