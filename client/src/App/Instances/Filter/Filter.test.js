import React from 'react';
import {shallow} from 'enzyme';

import Filter from './';

describe('Filter', () => {
  let node;
  const mockOnChange = jest.fn();
  let active = false;
  let incidents = false;
  beforeEach(() => {
    node = shallow(
      <Filter
        type={'running'}
        filter={{active, incidents}}
        onChange={mockOnChange}
      />
    );
  });

  it('should render three checkboxes', () => {
    expect(node.children().find('Checkbox').length).toBe(3);
  });

  it('should render the Checkboxes "checked" according to the filter props', () => {
    //given
    const activityFilter = false;
    const incidentsFilter = false;
    expect(node.instance().props.filter.active).toBe(activityFilter);
    expect(node.instance().props.filter.incidents).toBe(incidentsFilter);
    //then
    expect(
      node
        .childAt(1)
        .childAt(1)
        .find('Checkbox')
        .props().isChecked
    ).toBe(incidentsFilter);

    expect(
      node
        .childAt(1)
        .childAt(0)
        .find('Checkbox')
        .props().isChecked
    ).toBe(activityFilter);
  });

  it('parent checkbox should be indetermniate', () => {
    let node;
    const mockOnChange = jest.fn();
    let active = false;
    let incidents = true;

    node = shallow(
      <Filter
        type={'running'}
        filter={{active, incidents}}
        onChange={mockOnChange}
      />
    );
    expect(node.childAt(0).find('Checkbox')).toMatchSnapshot();
  });

  it('parent checkbox should be unchecked', () => {
    let node;
    const mockOnChange = jest.fn();
    let active = false;
    let incidents = false;

    node = shallow(
      <Filter
        type={'running'}
        filter={{active, incidents}}
        onChange={mockOnChange}
      />
    );
    expect(node.childAt(0).find('Checkbox')).toMatchSnapshot();
  });
});
