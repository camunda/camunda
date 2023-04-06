/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

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

  const inputGroup = node.find('label InputGroup');

  expect(inputGroup.childAt(0).prop('id')).toBe('input1');
  expect(inputGroup.childAt(0).childAt(0).prop('value')).toBe('test');
  expect(inputGroup.childAt(1).prop('id')).toBe('input2');
});

it('should prevent the default action on form submit events', () => {
  const spy = jest.fn();
  const evt = {preventDefault: jest.fn()};

  const node = shallow(<Form onSubmit={spy} />);
  node.simulate('submit', evt);

  expect(evt.preventDefault).toHaveBeenCalled();
  expect(spy).toHaveBeenCalledWith(evt);
});

it('should render title', () => {
  const node = shallow(<Form title="this is a title" />);

  expect(node.find('.formTitle')).toHaveText('this is a title');
});

it('should render description', () => {
  const node = shallow(<Form description="this is a description" />);

  expect(node.find('.formDescription')).toHaveText('this is a description');
});

describe('Form.Group', () => {
  it('should render the group with children', () => {
    const node = shallow(
      <Form.Group>
        <div id="someId">some</div>
      </Form.Group>
    );

    expect(node.find('.FormGroup div').prop('id')).toBe('someId');
    expect(node.find('.FormGroup div').text()).toBe('some');
  });

  it('should add noSpacing class', () => {
    const node = shallow(
      <Form.Group noSpacing>
        <div id="someId">some</div>
      </Form.Group>
    );

    expect(node.find('.FormGroup').prop('className')).toContain('noSpacing');
  });
});

describe('Form.InputGroup', () => {
  it('should render the group with children', () => {
    const node = shallow(
      <Form.InputGroup>
        <div id="someId">some</div>
      </Form.InputGroup>
    );

    expect(node.find('.InputGroup div').prop('id')).toBe('someId');
    expect(node.find('.InputGroup div').text()).toBe('some');
  });
});
