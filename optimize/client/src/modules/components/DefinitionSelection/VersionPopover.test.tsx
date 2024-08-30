/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {Version} from './service';
import VersionPopover from './VersionPopover';

const versions: Version[] = [
  {version: '3', versionTag: 'v3'},
  {version: '2', versionTag: null},
  {version: '1', versionTag: 'v1'},
];

const props = {
  onChange: jest.fn(),
};

beforeEach(() => {
  jest.clearAllMocks();
});

it('should call the provided onChange function', () => {
  const node = shallow(<VersionPopover {...props} versions={versions} selected={['3']} />);

  node.find('RadioButton').first().simulate('click');

  expect(props.onChange).toHaveBeenCalledWith(['all']);
});

it('should disable the specific selection when all or latest is selected', () => {
  const node = shallow(<VersionPopover {...props} versions={versions} selected={['all']} />);
  expect(node.find('.specificVersions')).toHaveClassName('disabled');

  node.setProps({selected: ['latest']});
  expect(node.find('.specificVersions')).toHaveClassName('disabled');

  node.setProps({selected: ['1']});
  expect(node.find('.specificVersions')).not.toHaveClassName('disabled');
});

it('should construct the label based on the selected versions', () => {
  const node = shallow(<VersionPopover {...props} versions={versions} selected={[]} />);

  expect(node.prop('trigger').props.children).toBe('None');

  node.setProps({selected: ['1']});

  expect(node.prop('trigger').props.children).toBe('1');

  node.setProps({selected: ['2', '1']});

  expect(node.prop('trigger').props.children).toBe('2, 1');

  node.setProps({selected: ['all']});

  expect(node.prop('trigger').props.children).toBe('All');

  node.setProps({selected: ['latest']});

  expect(node.prop('trigger').props.children).toBe('Latest : 3');
});

it('should check the versions based on the selected specific versions', () => {
  const node = shallow(
    <VersionPopover
      {...props}
      versions={versions}
      selected={['latest']}
      selectedSpecificVersions={['3', '1']}
    />
  );

  expect(node.prop('trigger').props.children).toBe('Latest : 3');
});

it('should not crash, but be disabled if no versions are provided', () => {
  const node = shallow(<VersionPopover {...props} selected={[]} />);
  expect(node.prop('trigger').props.disabled).toBe(true);
});

it('should diplay a loading state the inputs while loading', () => {
  const node = shallow(<VersionPopover {...props} selected={[]} loading />);

  expect(node.find('ReplaceContentOnLoading').at(0).dive().find('RadioButtonSkeleton').length).toBe(
    3
  );
  expect(node.find('ReplaceContentOnLoading').at(1).dive().find('CheckboxSkeleton').length).toBe(3);
});
