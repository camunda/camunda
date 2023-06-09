/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {LoadingIndicator, Typeahead} from 'components';
import {getCollection} from 'services';

import {DefinitionSelection} from './DefinitionSelection';
import VersionPopover from './VersionPopover';

import {loadDefinitions, loadVersions, loadTenants} from './service';
import MultiDefinitionSelection from './MultiDefinitionSelection';

jest.mock('./service', () => ({
  loadDefinitions: jest.fn().mockReturnValue([
    {
      key: 'foo',
      name: 'Foo',
    },
    {
      key: 'bar',
      name: 'Bar',
    },
  ]),
  loadVersions: jest.fn().mockReturnValue([
    {
      version: '3',
      versionTag: 'Tag',
    },
    {
      version: '2',
      versionTag: 'Another Tag',
    },
    {
      version: '1',
      versionTag: 'Tag',
    },
  ]),
  loadTenants: jest.fn().mockReturnValue([
    {
      tenants: [
        {
          id: 'a',
          name: 'Tenant A',
        },
        {
          id: 'b',
          name: 'Tenant B',
        },
        {
          id: 'c',
          name: 'Tenant C',
        },
      ],
    },
  ]),
}));

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    getCollection: jest.fn(),
  };
});
jest.mock(
  'debouncePromise',
  () =>
    () =>
    (fn, _, ...args) =>
      fn(...args)
);

const spy = jest.fn();

const props = {
  type: 'process',
  tenants: [],
  location: {pathname: '/report/1'},
  onChange: spy,
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

beforeEach(() => {
  spy.mockClear();
  getCollection.mockReset();
  loadDefinitions.mockClear();
  loadVersions.mockClear();
  loadTenants.mockClear();
});

it('should render without crashing', () => {
  shallow(<DefinitionSelection {...props} />);
});

it('should display a loading indicator', () => {
  const node = shallow(<DefinitionSelection {...props} />);

  expect(node.find(LoadingIndicator)).toExist();
});

it('should initially load all definitions', () => {
  shallow(<DefinitionSelection {...props} />);

  expect(loadDefinitions).toHaveBeenCalledWith(props.type, undefined, undefined);
});

it('should load defintions in scope of collection', () => {
  getCollection.mockReturnValue('123');
  shallow(<DefinitionSelection {...props} />);

  expect(loadDefinitions).toHaveBeenCalledWith(props.type, '123', undefined);
});

it('should only load definitions for which camunda events were imported', () => {
  shallow(<DefinitionSelection {...props} camundaEventImportedOnly />);

  expect(loadDefinitions).toHaveBeenCalledWith(props.type, undefined, true);
});

it('should load versions and tenants when key is selected', async () => {
  const node = await shallow(<DefinitionSelection {...props} />);
  await flushPromises();

  await node.find(Typeahead).simulate('change', 'foo');
  await flushPromises();

  expect(loadVersions).toHaveBeenCalledWith(props.type, undefined, 'foo');
  expect(loadTenants).toHaveBeenCalledWith(
    props.type,
    [{key: 'foo', versions: ['all']}],
    undefined
  );
});

it('should update to all version when key is selected', async () => {
  const node = await shallow(<DefinitionSelection {...props} />);
  await flushPromises();

  await node.find(Typeahead).simulate('change', 'foo');
  await flushPromises();

  expect(spy.mock.calls[0][0].versions).toEqual(['all']);
});

it('should store specifically selected versions', async () => {
  const node = await shallow(<DefinitionSelection {...props} definitionKey="foo" />);
  await flushPromises();

  await node.find(VersionPopover).simulate('change', ['3', '1']);
  expect(node.find(VersionPopover).prop('selectedSpecificVersions')).toEqual(['3', '1']);

  await node.find(VersionPopover).simulate('change', ['latest']);
  expect(node.find(VersionPopover).prop('selectedSpecificVersions')).toEqual(['3', '1']);

  await node.find(VersionPopover).simulate('change', ['2']);
  expect(node.find(VersionPopover).prop('selectedSpecificVersions')).toEqual(['2']);
});

it('should update definition if versions is changed', async () => {
  const node = await shallow(<DefinitionSelection definitionKey="foo" {...props} />);
  await flushPromises();

  await node.find(VersionPopover).simulate('change', ['1']);
  await flushPromises();

  expect(spy.mock.calls[0][0].versions).toEqual(['1']);
});

it('should disable typeahead if no reports are available', async () => {
  loadDefinitions.mockReturnValueOnce([]);
  const node = await shallow(<DefinitionSelection {...props} />);
  await flushPromises();

  expect(node.find('Typeahead')).toExist();
  expect(node.find('Typeahead')).toBeDisabled();
});

it('should set key and version, if process definition is already available', async () => {
  const definitionConfig = {
    definitionKey: 'bar',
    versions: ['1'],
  };
  const node = await shallow(<DefinitionSelection {...definitionConfig} {...props} />);
  await flushPromises();

  expect(node.find('.name')).toHaveProp('initialValue', 'bar');
  expect(node.find(VersionPopover).prop('selected')).toEqual(['1']);
});

it('should not call on change if key didnt change', async () => {
  const definitionConfig = {
    definitionKey: 'bar',
    versions: ['1'],
  };
  const node = await shallow(<DefinitionSelection {...definitionConfig} {...props} />);
  await flushPromises();

  spy.mockClear();

  await node.find(Typeahead).simulate('change', 'bar');
  await flushPromises();

  expect(spy).not.toHaveBeenCalled();
});

it('should render diagram if enabled and definition is selected', async () => {
  const definitionConfig = {
    definitionKey: 'foo',
    versions: ['2'],
    xml: 'some xml',
  };
  const node = await shallow(
    <DefinitionSelection renderDiagram {...definitionConfig} {...props} />
  );
  await flushPromises();

  expect(node.find('.diagram')).toExist();
});

it('should disable version selection, if no key is selected', async () => {
  const node = await shallow(<DefinitionSelection {...props} />);
  await flushPromises();

  const versionSelect = node.find(VersionPopover);
  expect(versionSelect.prop('disabled')).toBeTruthy();
});

it('should show a note if more than one version is selected', async () => {
  const node = await shallow(
    <DefinitionSelection {...props} definitionKey="foo" versions={['all']} />
  );
  await flushPromises();

  expect(node.find('Message')).toMatchSnapshot();
  await node.find(VersionPopover).simulate('change', ['1', '2']);
  expect(node.find('Message')).toExist();
  await node.find(VersionPopover).simulate('change', ['1']);
  expect(node.find('Message')).not.toExist();
});

it('should show an info message to add sources', async () => {
  loadDefinitions.mockReturnValueOnce([]);
  getCollection.mockReturnValue('123');
  const node = await shallow(<DefinitionSelection {...props} />);
  await flushPromises();

  expect(node.find('.info')).toMatchSnapshot();
});

it('should show an info message if specified by props', async () => {
  const node = await shallow(
    <DefinitionSelection {...props} definitionKey="foo" infoMessage="test message" />
  );
  await flushPromises();

  expect(node.find('Message').props().children).toBe('test message');
});

it('should pass an id for every entry to the typeahead', async () => {
  loadDefinitions.mockReturnValueOnce([
    {
      key: 'foo',
      name: 'Foo Definition',
    },
    {
      key: 'bar',
      name: 'Bar Definition',
    },
  ]);

  const node = await shallow(<DefinitionSelection {...props} />);
  await flushPromises();

  expect(node.find('Typeahead')).toMatchSnapshot();
});

it('should construct a popover title', async () => {
  const node = await shallow(<DefinitionSelection {...props} />);
  await flushPromises();

  expect(node.find('.DefinitionSelection')).toHaveProp('title', 'Select Process');

  loadTenants.mockReturnValueOnce([
    {
      tenants: [
        {
          id: null,
          name: 'Not defined',
        },
      ],
    },
  ]);
  const nodeWithData = await shallow(
    <DefinitionSelection
      {...props}
      definitionKey="bar"
      versions={['1']}
      xml="whatever"
      tenants={[null]}
    />
  );
  await flushPromises();

  expect(nodeWithData.find('.DefinitionSelection')).toHaveProp('title', 'Bar : 1');
});

it('should construct a popover title even without xml', async () => {
  const node = await shallow(
    <DefinitionSelection {...props} definitionKey="foo" versions={['1']} xml={null} tenants={[]} />
  );
  await flushPromises();

  expect(node.find('.DefinitionSelection')).toHaveProp('title', 'Foo : 1 : -');
});

it('should hide the tenant selection by default', async () => {
  const node = await shallow(<DefinitionSelection {...props} />);
  await flushPromises();

  expect(node.find('.container')).not.toHaveClassName('withTenants');
});

it('should preserve previously deselected tenants if version changes', async () => {
  const node = await shallow(
    <DefinitionSelection {...props} definitionKey="foo" versions={['1']} tenants={['a']} />
  );
  await flushPromises();

  spy.mockClear();
  await node.find(VersionPopover).simulate('change', ['2']);
  await flushPromises();

  expect(spy).toHaveBeenCalledWith({
    key: 'foo',
    name: 'Foo',
    tenantIds: ['a'],
    versions: ['2'],
    identifier: 'definition',
  });
});

describe('tenants', () => {
  it('should construct a popover title for tenants', async () => {
    let node = await shallow(<DefinitionSelection {...props} />);
    await flushPromises();

    expect(node.find('.DefinitionSelection')).toHaveProp('title', 'Select Process');

    node = await shallow(
      <DefinitionSelection {...props} definitionKey="foo" versions={['3']} tenants={[]} />
    );
    await flushPromises();

    expect(node.find('.DefinitionSelection')).toHaveProp('title', 'Foo : 3 : -');

    node.find('TenantPopover').simulate('change', ['a']);
    expect(node.find('.DefinitionSelection')).toHaveProp('title', 'Foo : 3 : Tenant A');

    node.find('TenantPopover').simulate('change', ['a', 'b']);
    expect(node.find('.DefinitionSelection')).toHaveProp('title', 'Foo : 3 : Multiple');

    node.find('TenantPopover').simulate('change', ['a', 'b', 'c']);
    expect(node.find('.DefinitionSelection')).toHaveProp('title', 'Foo : 3 : All');
  });

  it('should show a tenant selection component', async () => {
    const node = await shallow(
      <DefinitionSelection {...props} definitionKey="foo" versions={['3']} />
    );
    await flushPromises();

    expect(node.find('.container')).toHaveClassName('withTenants');
  });

  it('should select all tenants when changing the definition', async () => {
    const node = await shallow(
      <DefinitionSelection {...props} definitionKey="bar" versions={['1']} />
    );
    await flushPromises();

    await node.find(Typeahead).simulate('change', 'foo');
    await flushPromises();

    expect(spy).toHaveBeenCalledWith({
      key: 'foo',
      name: 'Foo',
      tenantIds: ['a', 'b', 'c'],
      versions: ['all'],
      identifier: 'definition',
    });
  });
});

it('should display expanded definition selection without a popover if specified', async () => {
  const node = await shallow(<DefinitionSelection {...props} expanded />);
  await flushPromises();

  expect(node.find('.DefinitionSelection').type()).toBe('div');
});

it('should invoke onChange from MultiDefinitionSelection if selectedDefinitions is specified', async () => {
  const spy = jest.fn();
  const node = await shallow(
    <DefinitionSelection {...props} selectedDefinitions={[]} onChange={spy} />
  );

  await flushPromises();

  expect(node.find(Typeahead)).not.toExist();
  expect(node.find(MultiDefinitionSelection)).toExist();

  node.find(MultiDefinitionSelection).simulate('change', ['test']);
  expect(spy).toHaveBeenCalledWith(['test']);

  node.find(MultiDefinitionSelection).prop('changeDefinition')('foo');
  await flushPromises();
  expect(spy).toHaveBeenCalledWith([
    {
      identifier: 'definition',
      key: 'foo',
      name: 'Foo',
      tenantIds: ['a', 'b', 'c'],
      versions: ['all'],
    },
  ]);
});

it('should pass versionTooltip to the version popover', async () => {
  const node = await shallow(<DefinitionSelection {...props} versionTooltip={'test tooltip'} />);

  expect(node.find(VersionPopover).prop('tooltip')).toBe('test tooltip');
});
