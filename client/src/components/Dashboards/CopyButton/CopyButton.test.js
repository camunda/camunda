/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {copyEntity} from 'services';

import CopyButton from './CopyButton';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  copyEntity: jest.fn().mockReturnValue('id'),
}));

jest.mock('prompt', () => ({}));

const props = {
  addTile: jest.fn(),
};

it('should invoke copyEntity when copying optimize report with an id already assigned', async () => {
  const report = {
    position: {x: 0, y: 0},
    dimensions: {height: 2, width: 2},
    type: 'optimize_report',
    id: '1',
  };

  const node = shallow(<CopyButton tile={report} {...props} />);

  node.find('Button').simulate('click');

  await runAllEffects();
  await flushPromises();

  expect(copyEntity).toHaveBeenCalledWith('report', '1');
  expect(props.addTile).toHaveBeenCalledWith({
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
    configuration: {text: 'text'},
  };

  const node = shallow(<CopyButton tile={report} {...props} />);

  node.find('Button').simulate('click');

  expect(copyEntity).not.toHaveBeenCalled();
  expect(props.addTile).toHaveBeenCalledWith(report);
});
