/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import NodesTable from './NodesTable';

const props = {
  focus: 'focus',
  updateFocus: jest.fn(),
  values: {a: {unit: 'days', value: '12', operator: '>'}},
  nodeNames: {a: 'Element A'},
  onChange: jest.fn(),
};

it('should display a list of flow nodes in a table', async () => {
  const node = shallow(<NodesTable {...props} />);

  const body = node.find('Table').prop<string[][]>('body');

  expect(body[0]?.[0]).toBe('Element A');
});

it('should set invalid property for input if value is invalid', async () => {
  const node = shallow(
    <NodesTable {...props} values={{a: {unit: 'days', value: 'a', operator: '>'}}} />
  );

  expect(node.prop('body')[0][1].props.children[1].props).toHaveProperty('invalid', true);
});

it('should invoke update focus when changing a value of a flownode', async () => {
  const node = shallow(<NodesTable {...props} />);

  const body = node.find('Table').prop<JSX.Element[][]>('body');

  body[0]?.[1]?.props.children[0].props.onChange('<');

  expect(props.updateFocus).toHaveBeenCalledWith('a');
});
