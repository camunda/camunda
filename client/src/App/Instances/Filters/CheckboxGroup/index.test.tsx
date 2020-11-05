/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import CheckboxGroup from './index';

describe('CheckboxGroup', () => {
  let node: any;
  let active: any, incidents: any;

  const mockOnChange = jest.fn();

  it('should render three checkboxes', () => {
    //given
    active = false;
    incidents = false;

    node = shallow(
      <CheckboxGroup
        type={'running'}
        filter={{active, incidents}}
        onChange={mockOnChange}
      />
    );
    //then
    expect(node.children().find('Checkbox').length).toBe(3);
  });

  it('should render empty checkbox when no "isChecked" prop is passed', () => {
    //given
    incidents = false;
    active = undefined;
    node = shallow(
      <CheckboxGroup
        type={'running'}
        filter={{active, incidents}}
        onChange={mockOnChange}
      />
    );

    //then
    expect(node.find({label: 'Active'}).props().isChecked).toBe(false);
  });

  it('should render the checkboxes as "checked" according to the filter props', () => {
    //given
    active = true;
    incidents = false;

    node = shallow(
      <CheckboxGroup
        type={'running'}
        filter={{active, incidents}}
        onChange={mockOnChange}
      />
    );

    //then
    expect(node.find({label: 'Active'}).props().isChecked).toBe(active);
    expect(node.find({label: 'Incidents'}).props().isChecked).toBe(incidents);
  });

  describe('Parent Checkbox', () => {
    beforeEach(() => {
      node = shallow(
        <CheckboxGroup
          type={'running'}
          filter={{active, incidents}}
          onChange={mockOnChange}
        />
      );
    });

    it('should render as "indeterminate"', () => {
      // given
      node.setProps({filter: {active: false, incidents: true}});
      node.update();

      // then
      expect(
        node.find({label: 'Running Instances'}).props().isIndeterminate
      ).toBe(true);
    });

    it('should render as "unchecked"', () => {
      // given
      node.setProps({filter: {active: false, incidents: false}});
      node.update();

      // then
      expect(node.find({label: 'Running Instances'}).props().isChecked).toBe(
        false
      );
    });

    it('should render as "checked"', () => {
      // given
      node.setProps({filter: {active: true, incidents: true}});
      node.update();

      // then
      expect(node.find({label: 'Running Instances'}).props().isChecked).toBe(
        true
      );
    });
  });
});
