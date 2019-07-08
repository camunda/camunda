/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import Form from './Form';

it('should render without crashing', () => {
  shallow(<Form />);
});

it('should render a .Form className by default', () => {
  const node = shallow(<Form />);

  expect(node).toMatchSelector('.Form');
});

it('should merge and render additional classNames as provided as a property', () => {
  const node = shallow(<Form className="foo" />);

  expect(node).toMatchSelector('.Form.foo');
});

it('should render child elements and their props', () => {
  const node = shallow(
    <Form horizontal>
      <Form.Group>
        <label>
          <Form.InputGroup>
            <select id="input1">
              <option value="test">test</option>
            </select>
            <input id="input2" type="text" />
          </Form.InputGroup>
        </label>
      </Form.Group>
    </Form>
  );

  expect(node).toMatchSnapshot();
});
