/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {LabeledInput} from 'components';

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

  node
    .find(LabeledInput)
    .first()
    .simulate('change', {target: {checked: true}});

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

  expect(node.find('.VersionPopover').prop('title')).toBe('None');

  node.setProps({selected: ['1']});

  expect(node.find('.VersionPopover').prop('title')).toBe('1');

  node.setProps({selected: ['2', '1']});

  expect(node.find('.VersionPopover').prop('title')).toBe('2, 1');

  node.setProps({selected: ['all']});

  expect(node.find('.VersionPopover').prop('title')).toBe('All');

  node.setProps({selected: ['latest']});

  expect(node.find('.VersionPopover').prop('title')).toBe('Latest : 3');
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

  expect(node).toMatchSnapshot();
});

it('should not crash, but be disabled if no versions are provided', () => {
  const node = shallow(<VersionPopover {...props} selected={[]} />);
  expect(node.find('.VersionPopover').prop('disabled')).toBe(true);
});

it('should diplay a loading indicator and disable the inputs while loading', () => {
  const node = shallow(<VersionPopover {...props} selected={[]} loading />);

  expect(node.find('LoadingIndicator')).toExist();
  expect(node.find(LabeledInput).at(0).prop('disabled')).toBe(true);
});
