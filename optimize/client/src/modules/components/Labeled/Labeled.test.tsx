/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {MouseEvent, MouseEventHandler} from 'react';

import Labeled from './Labeled';

const props = {label: 'some label', id: 'someId'};

it('should pass props to label child element', () => {
  const node = shallow(
    <Labeled {...props}>
      <div className="test" />
    </Labeled>
  );

  expect(node.find('label')).toHaveProp('id', 'someId');
});
it('should include the child content', () => {
  const node = shallow(
    <Labeled {...props}>
      <div>some child content</div>
      <div>test</div>
    </Labeled>
  );

  expect(node).toIncludeText('some child content');
});

it('should can be disabled', () => {
  const node = shallow(<Labeled {...props} disabled />);

  expect(node).toHaveClassName('disabled');
});

it('should put the label at the end', () => {
  const node = shallow(
    <Labeled {...props} appendLabel>
      <span>something</span>
    </Labeled>
  );

  expect(node.find('span').at(1).text()).toBe('some label');
});

it('should catch click if not clicked on label or input', () => {
  const spy = jest.fn();
  const node = shallow(<Labeled {...props} />);

  node.find('label').prop<MouseEventHandler<HTMLElement>>('onClick')({
    target: {classList: {contains: () => false}, closest: () => false},
    preventDefault: spy,
  } as unknown as MouseEvent<HTMLElement>);

  expect(spy).toHaveBeenCalled();
});

it('should not catch click if clicked on label or input', () => {
  const spy = jest.fn();
  const node = shallow(<Labeled {...props} />);

  node.find('label').prop<MouseEventHandler<HTMLElement>>('onClick')({
    target: {classList: {contains: () => true}, closest: () => false},
    preventDefault: spy,
  } as unknown as MouseEvent<HTMLElement>);

  expect(spy).not.toHaveBeenCalled();
});
