/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from 'react';
import {shallow} from 'enzyme';
import {TextInput, Button} from '@carbon/react';

import {BPMNDiagram, TenantInfo} from 'components';
import {loadProcessDefinitionXml} from 'services';

import RenameVariablesModal from './RenameVariablesModal';
import DiagramModal from './DiagramModal';
import {DefinitionEditor} from './DefinitionEditor';
import {loadVersions} from './service';

jest.mock('config', () => ({getOptimizeProfile: jest.fn().mockReturnValue('ccsm')}));

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    loadProcessDefinitionXml: jest.fn().mockReturnValue('<diagram XML>'),
  };
});

jest.mock('./service', () => ({
  loadVersions: jest.fn().mockReturnValue([
    {version: 3, versionTag: 'Version 3'},
    {version: 2, versionTag: 'Version 3'},
    {version: 1, versionTag: 'Version 3'},
  ]),
  loadTenants: jest.fn().mockReturnValue([
    {
      tenants: [
        {id: null, name: 'Not Defined'},
        {id: 'a', name: 'Tenant A'},
      ],
    },
  ]),
}));

const props = {
  mightFail: (data, cb) => cb(data),
  type: 'process',
  definition: {
    key: 'definitionA',
    name: 'Definition A',
    displayName: 'Definition A',
    versions: ['all'],
    tenantIds: [null],
  },
  tenantInfo: [
    {id: null, name: 'Not Defined'},
    {id: 'a', name: 'Tenant A'},
  ],
};

it('should show available versions for the given definition', () => {
  const node = shallow(<DefinitionEditor {...props} />);
  runAllEffects();

  expect(node.find('VersionPopover').prop('versions')).toEqual(loadVersions());
});

it('should show available tenants for the given definition and versions', () => {
  const node = shallow(<DefinitionEditor {...props} />);

  expect(node.find('TenantPopover').prop('tenants')).toEqual(props.tenantInfo);
});

it('should show the readonly TenantInfo in self managed mode', async () => {
  const tenant = {id: '<default>', name: 'Default'};
  const node = shallow(<DefinitionEditor {...props} tenantInfo={[tenant]} />);

  await runAllEffects();

  expect(node.find(TenantInfo).prop('tenant')).toBe(tenant);
});

it('should allow users to set a display name', () => {
  const spy = jest.fn();
  const node = shallow(<DefinitionEditor {...props} onChange={spy} />);

  node.find(TextInput).simulate('change', {target: {value: 'new display name'}});
  node.find(TextInput).simulate('blur', {relatedTarget: {}});

  expect(spy.mock.calls[0][0].displayName).toBe('new display name');
});

it('should allow changing version and tenants', () => {
  const spy = jest.fn();
  const node = shallow(<DefinitionEditor {...props} onChange={spy} />);

  node.find('VersionPopover').simulate('change', ['3', '1']);
  expect(spy.mock.calls[0][0].versions).toEqual(['3', '1']);

  node.find('TenantPopover').simulate('change', [null, 'tenantV']);
  expect(spy.mock.calls[1][0].tenantIds).toEqual([null, 'tenantV']);
});

it('should show the diagram of the definition', () => {
  const node = shallow(<DefinitionEditor {...props} />);
  runAllEffects();

  expect(node.find('.diagram').find(BPMNDiagram)).toExist();
  expect(node.find('.diagram').find(BPMNDiagram).prop('xml')).toBe(loadProcessDefinitionXml());
});

it('should allow opening the diagram in a bigger modal', () => {
  const node = shallow(<DefinitionEditor {...props} />);
  runAllEffects();

  node.find('.diagram').find(Button).simulate('click');

  expect(node.find(DiagramModal).prop('open')).toBe(true);
  expect(node.find(DiagramModal).prop('xml')).toBe(loadProcessDefinitionXml());
});

it('should pass all tenants ids to the RenameVariablesModal', () => {
  const tenantInfo = [
    {id: 'a', name: 'A'},
    {id: 'b', name: 'B'},
  ];
  const node = shallow(<DefinitionEditor {...props} tenantInfo={tenantInfo} />);

  node.find(Button).last().simulate('click');

  expect(node.find(RenameVariablesModal).prop('availableTenants')).toEqual(['a', 'b']);
});

it('should invoke onChange when confirming the renamed variable modal', () => {
  const spy = jest.fn();
  const node = shallow(<DefinitionEditor {...props} onChange={spy} />);
  runAllEffects();

  node.find(Button).last().simulate('click');
  node.find(RenameVariablesModal).prop('onChange')();

  expect(spy).toHaveBeenCalled();
});

it('should pass filters to RenameVariablesModal', () => {
  const filters = [{appliedTo: ['all'], filterLevel: 'instance', type: 'assignee', data: []}];
  const node = shallow(<DefinitionEditor {...props} filters={filters} />);
  runAllEffects();

  node.find(Button).last().simulate('click');

  expect(node.find(RenameVariablesModal).prop('filters')).toEqual(filters);
});
