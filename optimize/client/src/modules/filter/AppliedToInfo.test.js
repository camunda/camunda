/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';

import AppliedToInfo from './AppliedToInfo';

const definitions = [
  {
    displayName: 'def 1',
    identifier: 'def1',
    key: 'def1',
  },
  {
    displayName: 'def 2',
    identifier: 'def2',
    key: 'def2',
  },
  {
    displayName: 'def 3',
    identifier: 'def3',
    key: 'def3',
  },
];

it('should show proper label when applied to all processes', () => {
  const filter = {appliedTo: ['all']};
  const node = shallow(<AppliedToInfo filter={filter} definitions={definitions} />);

  expect(node.find('p').text()).toBe('Applied to: all Processes');
});

it('should show proper label when applied to one process', () => {
  const filter = {appliedTo: ['def1']};
  const node = shallow(<AppliedToInfo filter={filter} definitions={definitions} />);

  expect(node.find('p').text()).toBe('Applied to: 1 Process');
});

it('should not show label when there is only one process', () => {
  const filter = {appliedTo: ['def1']};
  const node = shallow(
    <AppliedToInfo
      filter={filter}
      definitions={[
        {
          displayName: 'def 1',
          identifier: 'def1',
          key: 'def1',
        },
      ]}
    />
  );

  expect(node.find('p')).not.toExist();
});

it('should show list of all running processes in tooltip', () => {
  const filter = {appliedTo: ['all']};
  const node = shallow(<AppliedToInfo filter={filter} definitions={definitions} />);
  const tooltipContent = shallow(node.find('Tooltip').prop('label'));

  expect(tooltipContent.find('ul')).toIncludeText('def 1');
  expect(tooltipContent.find('ul')).toIncludeText('def 2');
  expect(tooltipContent.find('ul')).toIncludeText('def 3');
});

it('should show list of one running processes in tooltip', () => {
  const filter = {appliedTo: ['def1']};
  const node = shallow(<AppliedToInfo filter={filter} definitions={definitions} />);
  const tooltipContent = shallow(node.find('Tooltip').prop('label'));

  expect(tooltipContent.find('ul')).toIncludeText('def 1');
  expect(tooltipContent.find('ul')).not.toIncludeText('def 2');
  expect(tooltipContent.find('ul')).not.toIncludeText('def 3');
});
