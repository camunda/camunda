/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';

import {copyEntity} from 'services';
import {DashboardTile} from 'types';

import CopyButton from './CopyButton';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  copyEntity: jest.fn().mockReturnValue('id'),
}));

jest.mock('prompt', () => ({}));

const props = {
  onTileAdd: jest.fn(),
};

it('should invoke copyEntity when copying optimize report with an id already assigned', async () => {
  const report = {
    position: {x: 0, y: 0},
    dimensions: {height: 2, width: 2},
    type: 'optimize_report',
    id: '1',
  } as DashboardTile;

  const node = shallow(<CopyButton tile={report} {...props} />);

  node.find('Button').simulate('click');

  await runAllEffects();
  await flushPromises();

  expect(copyEntity).toHaveBeenCalledWith('report', '1');
  expect(props.onTileAdd).toHaveBeenCalledWith({
    dimensions: {height: 2, width: 2},
    id: 'id',
    position: {x: 0, y: 0},
    type: 'optimize_report',
  });
});

it('should simply copy tile when it does not have an id', async () => {
  const report = {
    position: {x: 0, y: 0},
    dimensions: {height: 2, width: 2},
    type: 'text',
    configuration: {text: null},
    id: '',
  } as DashboardTile;

  const node = shallow(<CopyButton tile={report} {...props} />);

  node.find('Button').simulate('click');

  expect(copyEntity).not.toHaveBeenCalled();
  expect(props.onTileAdd).toHaveBeenCalledWith(report);
});
