/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runAllEffects, runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {ExternalSource} from './ExternalSource';
import {loadExternalGroups} from './service';

jest.mock('./service', () => ({
  loadExternalGroups: jest.fn().mockReturnValue([null, 'group 1']),
}));

jest.mock(
  'debouncePromise',
  () =>
    () =>
    (fn, delay, ...args) =>
      fn(...args)
);
jest.mock('debounce', () => (fn) => fn);

const props = {
  empty: false,
  externalSources: [{type: 'external', configuration: {group: 'group 1'}}],
  existingExternalSources: [],
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

beforeEach(() => {
  loadExternalGroups.mockClear();
});

it('should match snapshot', async () => {
  const node = shallow(<ExternalSource {...props} />);

  runAllEffects();
  await flushPromises();

  expect(node).toMatchSnapshot();
});

it('should invoke onChange when selecting a group', () => {
  const spy = jest.fn();
  const node = shallow(<ExternalSource {...props} onChange={spy} />);

  node.find('Checklist').prop('onChange')(['testGroup']);

  expect(spy).toHaveBeenCalledWith([
    {type: 'external', configuration: {group: 'testGroup', includeAllGroups: false}},
  ]);
});

it('should invoke onChange when selecting a group', () => {
  const spy = jest.fn();
  const node = shallow(<ExternalSource {...props} onChange={spy} />);

  node.find('Checklist').prop('onChange')(['testGroup']);

  expect(spy).toHaveBeenCalledWith([
    {type: 'external', configuration: {group: 'testGroup', includeAllGroups: false}},
  ]);
});

it('should invoke onChange with one source to include all groups', () => {
  const spy = jest.fn();
  const node = shallow(<ExternalSource {...props} onChange={spy} />);

  const preItems = node.find('Checklist').prop('preItems');

  preItems.props.onChange({target: {checked: true}});

  expect(spy).toHaveBeenCalledWith([
    {configuration: {group: null, includeAllGroups: true}, type: 'external'},
  ]);
});

it('should select and disable all items if inlude all groups is selected', () => {
  const node = shallow(
    <ExternalSource
      {...props}
      externalSources={[{configuration: {group: null, includeAllGroups: true}, type: 'external'}]}
    />
  );

  expect(node.find('Checklist').prop('formatter')(['testGroup'])).toEqual([
    {
      id: 'testGroup',
      label: 'testGroup',
      checked: true,
      disabled: true,
    },
  ]);
});

it('should display empty state with link to docs', () => {
  const node = shallow(<ExternalSource {...props} empty={true} />);

  expect(node).toMatchSnapshot();
});

it('should add search params to the load groups request', () => {
  const node = shallow(<ExternalSource {...props} />);

  node.find('Checklist').prop('onSearch')('group 1');
  runAllEffects();
  runLastEffect();

  expect(loadExternalGroups).toHaveBeenCalledWith({limit: 11, searchTerm: 'group 1'});
});

it('should increase the limit by 10 when clicking loadMore button', async () => {
  const groups = Array(11).fill('testGroup');
  loadExternalGroups.mockReturnValue(groups);
  const node = shallow(<ExternalSource {...props} />);

  runAllEffects();
  await flushPromises();

  node.find('.loadMore').simulate('click');
  runLastEffect();

  expect(loadExternalGroups).toHaveBeenCalledWith({limit: 21, searchTerm: ''});
});

it('should disable deselection of existing groups', () => {
  const node = shallow(
    <ExternalSource {...props} existingExternalSources={[{configuration: {group: 'group 1'}}]} />
  );

  expect(node.find('Checklist').prop('formatter')(['group 1'], ['group 1'])).toEqual([
    {
      id: 'group 1',
      label: 'group 1',
      checked: true,
      disabled: true,
    },
  ]);
});

it('should disable deselection of all events group if it exists', () => {
  const node = shallow(
    <ExternalSource
      {...props}
      existingExternalSources={[{configuration: {includeAllGroups: true}}]}
    />
  );
  const preItems = node.find('Checklist').prop('preItems');
  expect(preItems.props.checked).toBe(true);
  expect(preItems.props.disabled).toBe(true);
  expect(node.find('Checklist').prop('formatter')(['group 1'], ['group 1'])).toEqual([
    {
      id: 'group 1',
      label: 'group 1',
      checked: true,
      disabled: true,
    },
  ]);
});
