/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {LoadingIndicator} from 'components';

import DefinitionSelection from './DefinitionSelection';
import VersionPopover from './VersionPopover';
import TenantPopover from './TenantPopover';

import {loadDefinitions, getCollection} from 'services';

jest.mock('react-router-dom', () => {
  const rest = jest.requireActual('react-router-dom');
  return {
    ...rest,
    withRouter: a => a
  };
});

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    loadDefinitions: jest.fn().mockReturnValue([
      {
        key: 'foo',
        name: 'Foo',
        versions: [
          {
            version: '3',
            versionTag: 'Tag',
            tenants: [
              {
                id: null,
                name: 'Not defined'
              }
            ]
          },
          {
            version: '2',
            versionTag: 'Another Tag',
            tenants: [
              {
                id: 'a',
                name: 'Tenant A'
              },
              {
                id: 'b',
                name: 'Tenant B'
              }
            ]
          },
          {
            version: '1',
            versionTag: 'Tag',
            tenants: [
              {
                id: 'a',
                name: 'Tenant A'
              },
              {
                id: 'c',
                name: 'Tenant C'
              }
            ]
          }
        ],
        allTenants: [
          {
            id: null,
            name: 'Not defined'
          },
          {
            id: 'a',
            name: 'Tenant A'
          },
          {
            id: 'b',
            name: 'Tenant B'
          },
          {
            id: 'c',
            name: 'Tenant C'
          }
        ]
      },
      {
        key: 'bar',
        name: 'Bar',
        versions: [
          {
            version: '1',
            versionTag: null,
            tenants: [
              {
                id: null,
                name: 'Not defined'
              }
            ]
          }
        ],
        allTenants: [
          {
            id: null,
            name: 'Not defined'
          }
        ]
      }
    ]),
    getCollection: jest.fn()
  };
});

const spy = jest.fn();

const props = {
  type: 'process',
  tenants: [],
  location: {pathname: '/report/1'},
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

  expect(loadDefinitions).toHaveBeenCalledWith(props.type, undefined);
});

it('should load defintions in scope of collection', () => {
  getCollection.mockReturnValue('123');
  shallow(<DefinitionSelection {...props} />);

  expect(loadDefinitions).toHaveBeenCalledWith(props.type, '123');
});

it('should update to most recent version when key is selected', async () => {
  spy.mockClear();
  const node = await shallow(<DefinitionSelection {...props} />);

  await node.instance().changeDefinition('foo');

  expect(spy.mock.calls[0][0].versions).toEqual(['3']);
});

it('should store specifically selected versions', async () => {
  const node = await shallow(<DefinitionSelection {...props} definitionKey="foo" />);

  node.instance().changeVersions(['3', '1']);
  expect(node.find(VersionPopover).prop('selectedSpecificVersions')).toEqual(['3', '1']);

  node.instance().changeVersions(['latest']);
  expect(node.find(VersionPopover).prop('selectedSpecificVersions')).toEqual(['3', '1']);

  node.instance().changeVersions(['2']);
  expect(node.find(VersionPopover).prop('selectedSpecificVersions')).toEqual(['2']);
});

it('should update definition if versions is changed', async () => {
  spy.mockClear();
  const node = await shallow(<DefinitionSelection definitionKey="foo" {...props} />);

  await node.instance().changeVersions(['1']);

  expect(spy.mock.calls[0][0].versions).toEqual(['1']);
});

it('should disable typeahead if no reports are avaialbe', async () => {
  loadDefinitions.mockReturnValueOnce([]);
  const node = await shallow(<DefinitionSelection {...props} />);

  expect(node.find('Typeahead')).toExist();
  expect(node.find('Typeahead')).toBeDisabled();
});

it('should set key and version, if process definition is already available', async () => {
  const definitionConfig = {
    definitionKey: 'bar',
    versions: ['1']
  };
  const node = await shallow(<DefinitionSelection {...definitionConfig} {...props} />);

  expect(node.find('.name')).toHaveProp('initialValue', 'bar');
  expect(node.find(VersionPopover).prop('selected')).toEqual(['1']);
});

it('should not call on change if key didnt change', async () => {
  const definitionConfig = {
    definitionKey: 'bar',
    versions: ['1']
  };
  const node = await shallow(<DefinitionSelection {...definitionConfig} {...props} />);

  spy.mockClear();
  await node.instance().changeDefinition('bar');
  expect(spy).not.toHaveBeenCalled();
});

it('should call onChange function on change of the definition', async () => {
  spy.mockClear();
  const definitionConfig = {
    definitionKey: 'foo',
    versions: ['2']
  };
  const node = await shallow(<DefinitionSelection {...definitionConfig} {...props} />);

  await node.instance().changeVersions(['1']);

  expect(spy).toHaveBeenCalled();
});

it('should render diagram if enabled and definition is selected', async () => {
  const definitionConfig = {
    definitionKey: 'foo',
    versions: ['2'],
    xml: 'some xml'
  };
  const node = await shallow(
    <DefinitionSelection renderDiagram {...definitionConfig} {...props} />
  );

  expect(node.find('.diagram')).toExist();
});

it('should disable version selection, if no key is selected', async () => {
  const node = await shallow(<DefinitionSelection {...props} />);

  const versionSelect = node.find(VersionPopover);
  expect(versionSelect.prop('disabled')).toBeTruthy();
});

it('should show a note if more than one version is selected', async () => {
  const node = await shallow(
    <DefinitionSelection {...props} definitionKey="foo" versions={['all']} />
  );

  expect(node.find('Message')).toMatchSnapshot();
  node.setProps({versions: ['1', '2']});
  expect(node.find('Message')).toExist();
  node.setProps({versions: ['1']});
  expect(node.find('Message')).not.toExist();
});

it('should show an info message to add sources', async () => {
  loadDefinitions.mockReturnValueOnce([]);
  const node = await shallow(<DefinitionSelection {...props} />);

  expect(node.find('.info')).toMatchSnapshot();
});

it('should show an info message if specified by props', async () => {
  const node = await shallow(
    <DefinitionSelection {...props} definitionKey="foo" infoMessage="test message" />
  );

  expect(node.find('Message').props().children).toBe('test message');
});

it('should pass an id for every entry to the typeahead', async () => {
  loadDefinitions.mockReturnValueOnce([
    {
      key: 'foo',
      name: 'Foo Definition',
      versions: []
    },
    {
      key: 'bar',
      name: 'Bar Definition',
      versions: []
    }
  ]);

  const node = await shallow(<DefinitionSelection {...props} />);

  expect(node.find('Typeahead')).toMatchSnapshot();
});

it('should construct a popover title', async () => {
  const node = await shallow(<DefinitionSelection {...props} />);

  expect(node.find('Popover')).toHaveProp('title', 'Select Process');

  await node.setProps({
    definitionKey: 'bar',
    versions: ['1'],
    xml: 'whatever',
    tenants: [null]
  });

  expect(node.find('Popover')).toHaveProp('title', 'Bar : 1');
});

it('should construct a popover title even without xml', async () => {
  const node = await shallow(<DefinitionSelection {...props} />);

  await node.setProps({
    definitionKey: 'foo',
    versions: ['1'],
    xml: null,
    tenants: []
  });

  expect(node.find('Popover')).toHaveProp('title', 'Foo : 1 : -');
});

it('should hide the tenant selection by default', async () => {
  const node = await shallow(<DefinitionSelection {...props} />);

  expect(node.find('.container')).not.toHaveClassName('withTenants');
});

it('should merge tenenats from all selected versions and duplicates are filtered out', async () => {
  const node = await shallow(
    <DefinitionSelection {...props} definitionKey="foo" versions={['1', '2']} />
  );

  expect(node.find(TenantPopover)).toHaveProp('tenants', [
    {id: 'a', name: 'Tenant A'},
    {id: 'b', name: 'Tenant B'},
    {id: 'c', name: 'Tenant C'}
  ]);
});

it('should show all tenants if version is set to all', async () => {
  const node = await shallow(
    <DefinitionSelection {...props} definitionKey="foo" versions={['all']} />
  );

  expect(node.find(TenantPopover)).toHaveProp('tenants', [
    {id: null, name: 'Not defined'},
    {id: 'a', name: 'Tenant A'},
    {id: 'b', name: 'Tenant B'},
    {id: 'c', name: 'Tenant C'}
  ]);
});

it('should show tenants from latest version if version is set to latest', async () => {
  const node = await shallow(
    <DefinitionSelection {...props} definitionKey="foo" versions={['latest']} />
  );

  expect(node.find(TenantPopover)).toHaveProp('tenants', [{id: null, name: 'Not defined'}]);
});

it('should preserve previously deselected tenants if version changes', async () => {
  const node = await shallow(
    <DefinitionSelection {...props} definitionKey="foo" versions={['1']} tenants={['c']} />
  );

  spy.mockClear();
  await node.instance().changeVersions(['2']);
  expect(spy).toHaveBeenCalledWith({key: 'foo', name: 'Foo', tenantIds: ['b'], versions: ['2']});
});

describe('tenants', () => {
  beforeAll(() => {
    loadDefinitions.mockReturnValue([
      {
        key: 'foo',
        name: 'Foo',
        versions: [
          {
            version: '3',
            versionTag: 'Tag',
            tenants: [
              {
                id: null,
                name: 'Not defined'
              },
              {
                id: 'a',
                name: 'Tenant A'
              },
              {
                id: 'b',
                name: 'Tenant B'
              }
            ]
          },
          {
            version: '2',
            versionTag: null,
            tenants: [
              {
                id: 'a',
                name: 'Tenant A'
              },
              {
                id: 'b',
                name: 'Tenant B'
              }
            ]
          },
          {
            version: '1',
            versionTag: 'Tag',
            tenants: [
              {
                id: 'c',
                name: 'Tenant C'
              }
            ]
          }
        ],
        allTenants: [
          {
            id: null,
            name: 'Not defined'
          },
          {
            id: 'a',
            name: 'Tenant A'
          },
          {
            id: 'b',
            name: 'Tenant B'
          },
          {
            id: 'c',
            name: 'Tenant C'
          }
        ]
      },
      {
        key: 'bar',
        name: 'Bar Definition',
        versions: []
      }
    ]);
  });

  it('should construct a popover title for tenants', async () => {
    const node = await shallow(<DefinitionSelection {...props} />);

    expect(node.find('Popover')).toHaveProp('title', 'Select Process');

    await node.setProps({
      definitionKey: 'foo',
      versions: ['3'],
      xml: 'whatever',
      tenants: []
    });

    expect(node.find('Popover')).toHaveProp('title', 'Foo : 3 : -');

    await node.setProps({
      tenants: ['a']
    });

    expect(node.find('Popover')).toHaveProp('title', 'Foo : 3 : Tenant A');

    await node.setProps({
      tenants: ['a', 'b']
    });

    expect(node.find('Popover')).toHaveProp('title', 'Foo : 3 : Multiple');
    await node.setProps({
      tenants: [null, 'a', 'b']
    });

    expect(node.find('Popover')).toHaveProp('title', 'Foo : 3 : All');
  });

  it('should show a tenant selection component', async () => {
    const node = await shallow(
      <DefinitionSelection {...props} definitionKey="foo" versions={['3']} />
    );

    expect(node.find('.container')).toHaveClassName('withTenants');
  });

  it('should select all tenants when changing the definition', async () => {
    const node = await shallow(
      <DefinitionSelection {...props} definitionKey="bar" versions={['1']} />
    );

    spy.mockClear();
    node.instance().changeDefinition('foo');
    expect(spy).toHaveBeenCalledWith({
      key: 'foo',
      name: 'Foo',
      tenantIds: [null, 'a', 'b'],
      versions: ['3']
    });
  });
});

it('should display expanded definition selection without a popover if specified', async () => {
  const node = await shallow(<DefinitionSelection {...props} expanded />);

  expect(node.find('.DefinitionSelection').type()).toBe('div');
});
