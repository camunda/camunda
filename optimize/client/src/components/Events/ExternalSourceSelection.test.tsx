/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {runLastEffect, runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';

import {Checklist} from 'components';

import ExternalSourceSelection from './ExternalSourceSelection';
import {ExternalSource, loadExternalGroups} from './service';

jest.mock('./service', () => ({
  loadExternalGroups: jest.fn().mockReturnValue([null, 'group 1']),
}));

jest.mock(
  'debouncePromise',
  () =>
    () =>
    (fn: jest.Mock, delay: number, ...args: unknown[]) =>
      fn(...args)
);
jest.mock('debounce', () => (fn: jest.Mock) => fn);
jest.mock('hooks', () => ({
  ...jest.requireActual('hooks'),
  useErrorHandling: () => ({
    mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  }),
}));

const props = {
  empty: false,
  externalSources: [
    {type: 'external', configuration: {includeAllGroups: false, group: 'group 1'}},
  ] as ExternalSource[],
  existingExternalSources: [],
  onChange: () => {},
};

beforeEach(() => {
  (loadExternalGroups as jest.Mock).mockClear();
});

it('should match snapshot', async () => {
  const node = shallow(<ExternalSourceSelection {...props} />);

  runAllEffects();
  await flushPromises();

  expect(node).toMatchSnapshot();
});

it('should invoke onChange when selecting a group', () => {
  const spy = jest.fn();
  const node = shallow(<ExternalSourceSelection {...props} onChange={spy} />);
  node.find('Checklist').simulate('change', ['testGroup']);

  expect(spy).toHaveBeenCalledWith([
    {type: 'external', configuration: {group: 'testGroup', includeAllGroups: false}},
  ]);
});

it('should invoke onChange when selecting a group', () => {
  const spy = jest.fn();
  const node = shallow(<ExternalSourceSelection {...props} onChange={spy} />);

  node.find('Checklist').simulate('change', ['testGroup']);

  expect(spy).toHaveBeenCalledWith([
    {type: 'external', configuration: {group: 'testGroup', includeAllGroups: false}},
  ]);
});

it('should invoke onChange with one source to include all groups', () => {
  const spy = jest.fn();
  const node = shallow(<ExternalSourceSelection {...props} onChange={spy} />);

  const preItems = node
    .find('Checklist')
    .prop<{content: [{props: {onSelect: (evt: any) => void}}]}>('preItems');

  preItems.content[0]?.props.onSelect({target: {checked: true}});

  expect(spy).toHaveBeenCalledWith([
    {configuration: {group: null, includeAllGroups: true}, type: 'external'},
  ]);
});

it('should select and disable all items if inlude all groups is selected', () => {
  const node = shallow(
    <ExternalSourceSelection
      {...props}
      externalSources={[{configuration: {group: null, includeAllGroups: true}, type: 'external'}]}
    />
  );

  expect(node.find(Checklist).prop('formatter')(['testGroup'], [])).toEqual([
    {
      id: 'testGroup',
      label: 'testGroup',
      checked: true,
      disabled: true,
    },
  ]);
});

it('should display empty state with link to docs', () => {
  const node = shallow(<ExternalSourceSelection {...props} empty={true} />);

  expect(node).toMatchSnapshot();
});

it('should add search params to the load groups request', () => {
  const node = shallow(<ExternalSourceSelection {...props} />);

  node.find(Checklist).simulate('search', 'group 1');
  runAllEffects();
  runLastEffect();

  expect(loadExternalGroups).toHaveBeenCalledWith({limit: 11, searchTerm: 'group 1'});
});

it('should increase the limit by 10 when clicking loadMore button', async () => {
  const groups = Array(11).fill('testGroup');
  (loadExternalGroups as jest.Mock).mockReturnValue(groups);
  const node = shallow(<ExternalSourceSelection {...props} />);

  runAllEffects();
  await flushPromises();

  node.find('.loadMore').simulate('click');
  runLastEffect();

  expect(loadExternalGroups).toHaveBeenCalledWith({limit: 21, searchTerm: ''});
});

it('should disable deselection of existing groups', () => {
  const node = shallow(
    <ExternalSourceSelection
      {...props}
      existingExternalSources={[
        {type: 'external', configuration: {includeAllGroups: false, group: 'group 1'}},
      ]}
    />
  );

  expect(node.find(Checklist).prop('formatter')(['group 1'], ['group 1'])).toEqual([
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
    <ExternalSourceSelection
      {...props}
      existingExternalSources={[
        {type: 'external', configuration: {includeAllGroups: true, group: null}},
      ]}
    />
  );
  const preItems: {content: [{props: {checked: boolean; disabled: boolean}}]} = node
    .find('Checklist')
    .prop('preItems');
  expect(preItems.content[0].props.checked).toBe(true);
  expect(preItems.content[0].props.disabled).toBe(true);
  expect(node.find(Checklist).prop('formatter')(['group 1'], ['group 1'])).toEqual([
    {
      id: 'group 1',
      label: 'group 1',
      checked: true,
      disabled: true,
    },
  ]);
});
