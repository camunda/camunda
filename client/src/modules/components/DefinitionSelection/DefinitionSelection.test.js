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
        name: 'Foo',
        versions: [
          {tenants: [{id: null, name: 'Not defined'}], version: 'ALL'},
          {tenants: [{id: null, name: 'Not defined'}], version: '2'},
          {tenants: [{id: null, name: 'Not defined'}], version: '1'}
        ]
      },
      {
        key: 'bar',
        name: 'Bar',
        versions: [
          {tenants: [{id: null, name: 'Not defined'}], version: 'ALL'},
          {tenants: [{id: null, name: 'Not defined'}], version: '1'}
        ]
      }
    ]),
    extractDefinitionName: () => 'Definition'
  };
});

const spy = jest.fn();

const props = {
  type: 'process',
  tenants: [],
  onChange: spy
};

it('should render without crashing', () => {
  shallow(<DefinitionSelection {...props} />);
});

it('should display a loading indicator', () => {
  const node = shallow(<DefinitionSelection {...props} />);

  expect(node.find(LoadingIndicator)).toExist();
});

it('should initially load all definitions', () => {
  shallow(<DefinitionSelection {...props} />);

  expect(loadDefinitions).toHaveBeenCalled();
});

it('should update to most recent version when key is selected', async () => {
  spy.mockClear();
  const node = await shallow(<DefinitionSelection {...props} />);

  await node.instance().changeKey({key: 'foo'});

  expect(spy.mock.calls[0][1]).toBe('2');
});

it('should update definition if versions is changed', async () => {
  spy.mockClear();
  const node = await shallow(<DefinitionSelection definitionKey="foo" {...props} />);

  await node.instance().changeVersion('1', []);

  expect(spy.mock.calls[0][1]).toBe('1');
});

it('should disable typeahead if no reports are avaialbe', async () => {
  loadDefinitions.mockReturnValueOnce([]);
  const node = await shallow(<DefinitionSelection {...props} />);

  expect(node.find('Typeahead')).toExist();
  expect(node.find('Typeahead')).toBeDisabled();
});

it('should set key and version, if process definition is already available', async () => {
  const definitionConfig = {
    definitionKey: 'foo',
    definitionVersion: '2'
  };
  const node = await shallow(<DefinitionSelection {...definitionConfig} {...props} />);

  expect(node.find('.name')).toHaveProp('initialValue', {
    id: 'foo',
    key: 'foo',
    name: 'Foo',
    versions: [
      {tenants: [{id: null, name: 'Not defined'}], version: 'ALL'},
      {tenants: [{id: null, name: 'Not defined'}], version: '2'},
      {tenants: [{id: null, name: 'Not defined'}], version: '1'}
    ]
  });
  expect(node.find('.version')).toHaveProp('label', '2');
});

it('should call onChange function on change of the definition', async () => {
  spy.mockClear();
  const definitionConfig = {
    definitionKey: 'foo',
    definitionVersion: '2'
  };
  const node = await shallow(<DefinitionSelection {...definitionConfig} {...props} />);

  await node.instance().changeVersion('1', []);

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

  expect(node.find('.diagram')).toExist();
});

it('should disable version selection, if no key is selected', async () => {
  const node = await shallow(<DefinitionSelection {...props} />);

  const versionSelect = node.find('.version');
  expect(versionSelect.prop('disabled')).toBeTruthy();
});

it('should display all option in version selection if enabled', async () => {
  const node = await shallow(
    <DefinitionSelection {...props} enableAllVersionSelection definitionKey="foo" />
  );

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
    <DefinitionSelection {...props} enableAllVersionSelection definitionVersion="ALL" />
  );

  expect(node.find('.warning')).toExist();
});

it('should pass an id for every entry to the typeahead', async () => {
  loadDefinitions.mockReturnValueOnce([
    {
      key: 'foo',
      versions: [{id: 'procdef', key: 'foo', version: '2', name: 'A'}]
    },
    {
      key: 'bar',
      versions: [{id: 'anotherProcDef', key: 'bar', version: '1', name: 'A'}]
    }
  ]);

  const node = await shallow(<DefinitionSelection {...props} />);

  const values = node.find('Typeahead').prop('values');
  expect(values[0].id).toBe('foo');
  expect(values[1].id).toBe('bar');
});

it('should construct a popover title', async () => {
  const node = await shallow(<DefinitionSelection {...props} />);

  expect(node.find('Popover')).toHaveProp('title', 'Select Process');

  await node.setProps({
    definitionKey: 'foo',
    definitionVersion: '1',
    xml: 'whatever',
    tenants: [null]
  });

  expect(node.find('Popover')).toHaveProp('title', 'Definition : 1');
});

it('should hide the tenant selection by default', async () => {
  const node = await shallow(<DefinitionSelection {...props} />);

  expect(node.find('.container')).not.toHaveClassName('withTenants');
});

describe('tenants', () => {
  beforeAll(() => {
    loadDefinitions.mockReturnValue([
      {
        key: 'foo',
        name: 'Foo',
        versions: [
          {
            tenants: [
              {id: 'a', name: 'Tenant A'},
              {id: 'b', name: 'Tenant B'},
              {id: null, name: 'Not defined'}
            ],
            version: 'ALL'
          },
          {
            tenants: [
              {id: 'a', name: 'Tenant A'},
              {id: 'b', name: 'Tenant B'},
              {id: null, name: 'Not defined'}
            ],
            version: '1'
          }
        ]
      }
    ]);
  });

  it('should construct a popover title for tenants', async () => {
    const node = await shallow(<DefinitionSelection {...props} />);

    expect(node.find('Popover')).toHaveProp('title', 'Select Process');

    await node.setProps({
      definitionKey: 'foo',
      definitionVersion: '1',
      xml: 'whatever',
      tenants: []
    });

    expect(node.find('Popover')).toHaveProp('title', 'Definition : 1 : -');

    await node.setProps({
      tenants: ['a']
    });

    expect(node.find('Popover')).toHaveProp('title', 'Definition : 1 : Tenant A');

    await node.setProps({
      tenants: ['a', 'b']
    });

    expect(node.find('Popover')).toHaveProp('title', 'Definition : 1 : Multiple');
    await node.setProps({
      tenants: ['a', 'b', null]
    });

    expect(node.find('Popover')).toHaveProp('title', 'Definition : 1 : All');
  });

  it('should show a tenant selection component', async () => {
    const node = await shallow(
      <DefinitionSelection {...props} definitionKey="foo" definitionVersion="1" />
    );

    expect(node.find('.container')).toHaveClassName('withTenants');
  });
});
