/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {runAllEffects, runLastEffect} from '__mocks__/react';
import {shallow} from 'enzyme';

import ExternalSource from './ExternalSource';
import {loadExternalGroups} from './service';

jest.mock('./service', () => ({
  loadExternalGroups: jest.fn().mockReturnValue([null, 'group 1']),
}));

jest.mock(
  'debouncePromise',
  () =>
    () =>
    (fn: (...args: any[]) => void, delay: number, ...args: any[]) =>
      fn(...args)
);
jest.mock('debounce', () => (fn: Function) => fn);

jest.mock('hooks', () => ({
  useErrorHandling: () => ({
    mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  }),
}));

const props = {
  empty: false,
  externalSources: [{type: 'external', configuration: {group: 'group 1'}}],
  existingExternalSources: [],
  onChange: jest.fn(),
};

beforeEach(() => {
  (loadExternalGroups as jest.Mock).mockClear();
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

  node.find('Checklist').prop<(values: string[]) => void>('onChange')(['testGroup']);

  expect(spy).toHaveBeenCalledWith([
    {type: 'external', configuration: {group: 'testGroup', includeAllGroups: false}},
  ]);
});

it('should invoke onChange when selecting a group', () => {
  const spy = jest.fn();
  const node = shallow(<ExternalSource {...props} onChange={spy} />);

  node.find('Checklist').prop<(values: string[]) => void>('onChange')(['testGroup']);

  expect(spy).toHaveBeenCalledWith([
    {type: 'external', configuration: {group: 'testGroup', includeAllGroups: false}},
  ]);
});

it('should invoke onChange with one source to include all groups', () => {
  const spy = jest.fn();
  const node = shallow(<ExternalSource {...props} onChange={spy} />);

  const preItems = node.find('Checklist').prop('preItems') as {content: JSX.Element[]};

  preItems.content[0]?.props.onSelect({target: {checked: true}});

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

  expect(
    node.find('Checklist').prop<(...values: string[][]) => object>('formatter')(['testGroup'])
  ).toEqual([
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

  node.find('Checklist').prop<(value: string) => void>('onSearch')('group 1');
  runAllEffects();
  runLastEffect();

  expect(loadExternalGroups).toHaveBeenCalledWith({limit: 11, searchTerm: 'group 1'});
});

it('should increase the limit by 10 when clicking loadMore button', async () => {
  const groups = Array(11).fill('testGroup');
  (loadExternalGroups as jest.Mock).mockReturnValue(groups);
  const node = shallow(<ExternalSource {...props} />);

  runAllEffects();
  await flushPromises();

  node.find('.loadMore').simulate('click');
  runLastEffect();

  expect(loadExternalGroups).toHaveBeenCalledWith({limit: 21, searchTerm: ''});
});

it('should disable deselection of existing groups', () => {
  const node = shallow(
    <ExternalSource
      {...props}
      existingExternalSources={[{type: '', configuration: {group: 'group 1'}}]}
    />
  );

  expect(
    node.find('Checklist').prop<(...values: string[][]) => object>('formatter')(
      ['group 1'],
      ['group 1']
    )
  ).toEqual([
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
      existingExternalSources={[{type: '', configuration: {includeAllGroups: true, group: null}}]}
    />
  );
  const preItems = node.find('Checklist').prop('preItems') as {content: JSX.Element[]};
  expect(preItems.content[0]?.props.checked).toBe(true);
  expect(preItems.content[0]?.props.disabled).toBe(true);
  expect(
    node.find('Checklist').prop<(...values: string[][]) => object>('formatter')(
      ['group 1'],
      ['group 1']
    )
  ).toEqual([
    {
      id: 'group 1',
      label: 'group 1',
      checked: true,
      disabled: true,
    },
  ]);
});
