/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runLastEffect} from '__mocks__/react';
import {shallow} from 'enzyme';
import {Checkbox, TextInput, TextInputSkeleton, Toggle} from '@carbon/react';

import ShareEntity from './ShareEntity';

const props = {
  type: 'report',
  resourceId: 'resourceId',
  shareEntity: jest.fn(),
  revokeEntitySharing: jest.fn(),
  getSharedEntity: jest.fn().mockReturnValue(10),
};

const originalWindowLocation = window.location;

beforeEach(() => {
  jest.clearAllMocks();
  Object.defineProperty(window, 'location', {
    configurable: true,
    enumerable: true,
    value: new URL(window.location.href),
  });

  window.location.href = 'http://example.com/#/dashboard/1';
});

afterEach(() => {
  Object.defineProperty(window, 'location', {
    configurable: true,
    enumerable: true,
    value: originalWindowLocation,
  });
});

it('should initially get already shared entities', async () => {
  shallow(<ShareEntity {...props} />);
  await runLastEffect();

  expect(props.getSharedEntity).toHaveBeenCalled();
});

it('should share entity if is checked', async () => {
  const node = shallow(<ShareEntity {...props} />);
  await runLastEffect();

  node.find(Toggle).simulate('toggle', true);

  expect(props.shareEntity).toHaveBeenCalled();
});

it('should delete entity if sharing is revoked', async () => {
  const node = shallow(<ShareEntity {...props} />);
  await runLastEffect();

  node.find(Toggle).simulate('toggle', false);

  expect(props.revokeEntitySharing).toHaveBeenCalled();
});

it('should construct special link', async () => {
  const node = shallow(<ShareEntity {...props} type="report" />);
  await runLastEffect();

  expect(node.find('CopyToClipboard').at(0)).toHaveProp(
    'value',
    'http://example.com/external/#/share/report/10'
  );
});

it('should construct special link for embedding', async () => {
  const node = shallow(<ShareEntity {...props} type="report" />);
  await runLastEffect();
  Object.defineProperty(window.location, 'origin', {
    value: 'http://example.com',
  });

  const clipboardValue = node.find('CopyToClipboard').at(1).prop('value');

  expect(clipboardValue).toContain('<iframe src="http://example.com/external/#/share/report/10');
  expect(clipboardValue).toContain('mode=embed');
});

it('should include filters', async () => {
  const node = shallow(
    <ShareEntity
      {...props}
      type="dashboard"
      filter={[
        {data: undefined, type: 'runningInstancesOnly', filterLevel: 'instance', appliedTo: []},
      ]}
    />
  );
  await runLastEffect();

  node.find(Checkbox).simulate('change', {target: {checked: true}});

  const link = node.find('CopyToClipboard').at(0).prop('value');
  expect(link).toContain('?filter=');
  expect(link).toContain('runningInstancesOnly');
});

it('should display a loading indicator', async () => {
  const node = shallow(<ShareEntity {...props} />);
  runLastEffect();

  expect(node.find(TextInputSkeleton)).toExist();
  await flushPromises();
});

it('should disable all controlls when sharing is disabled', async () => {
  props.getSharedEntity.mockReturnValue(null);
  const node = shallow(<ShareEntity {...props} filter={[]} />);
  await runLastEffect();

  expect(node.find(TextInput).prop('disabled')).toBe(true);
  expect(node.find(Checkbox).prop('disabled')).toBe(true);
  expect(node.find('CopyToClipboard').at(0).prop('disabled')).toBe(true);
  expect(node.find('CopyToClipboard').at(1).prop('disabled')).toBe(true);
});

it('should enable all controlls when sharing is enabled', async () => {
  const node = shallow(<ShareEntity {...props} filter={[]} />);
  await runLastEffect();

  node.find(Toggle).simulate('toggle', true);

  expect(node.find(TextInput).prop('disabled')).toBe(false);
  expect(node.find(Checkbox).prop('disabled')).toBe(false);
  expect(node.find('CopyToClipboard').at(0).prop('disabled')).toBe(false);
  expect(node.find('CopyToClipboard').at(1).prop('disabled')).toBe(false);
});
