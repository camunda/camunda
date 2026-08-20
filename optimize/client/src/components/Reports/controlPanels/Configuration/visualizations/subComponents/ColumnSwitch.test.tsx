/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import ColumnSwitch from './ColumnSwitch';

const props = {
  switchId: 'id',
  label: 'this is a label',
  excludedColumns: ['a'],
  includedColumns: ['b'],
  onChange: jest.fn(),
};

it('should display a label', () => {
  const node = shallow(<ColumnSwitch {...props} />);

  expect(node.find('.ColumnSwitch').prop('labelText')).toBe('this is a label');
});

it('should not be checked when switch id is in excluded columns', () => {
  const node = shallow(<ColumnSwitch {...props} excludedColumns={['id']} />);

  expect(node.find('.ColumnSwitch').prop('toggled')).toBe(false);
});

it('should call onChange when toggled', () => {
  const spy = jest.fn();
  const node = shallow(<ColumnSwitch {...props} onChange={spy} />);

  node.find('.ColumnSwitch').simulate('toggle', true);

  expect(spy).toHaveBeenCalledWith({
    tableColumns: {excludedColumns: {$set: ['a']}, includedColumns: {$push: ['id']}},
  });

  node.find('.ColumnSwitch').simulate('toggle', false);

  expect(spy).toHaveBeenCalledWith({
    tableColumns: {excludedColumns: {$push: ['id']}, includedColumns: {$set: ['b']}},
  });
});
