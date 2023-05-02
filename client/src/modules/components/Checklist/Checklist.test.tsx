/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ChangeEvent} from 'react';
import {shallow} from 'enzyme';

import Checklist from './Checklist';

const props = {
  selectedItems: [{id: 'item1'}],
  allItems: [{id: 'item1'}, {id: 'item2'}],
  onChange: jest.fn(),
  formatter: jest.fn().mockReturnValue([
    {id: 'item1', checked: true, label: 'item 1'},
    {id: 'item2', checked: false, label: 'item 2'},
    {id: 'id3', label: 'unauthorized', disabled: true},
  ]),
};

beforeEach(() => props.onChange.mockClear());

it('should match snapshot', () => {
  const node = shallow(<Checklist {...props} />);

  expect(node).toMatchSnapshot();
});

it('should call the formatter with the list items data', () => {
  shallow(<Checklist {...props} />);

  expect(props.formatter).toHaveBeenCalledWith(props.allItems, props.selectedItems);
});

it('should invoke onChange with the updated selected items', () => {
  const node = shallow(<Checklist {...props} />);

  node.find({label: 'item 1'}).prop('onChange')({target: {checked: false}});

  expect(props.onChange).toHaveBeenCalledWith([]);

  node.find({label: 'item 2'}).prop('onChange')({target: {checked: true}});

  expect(props.onChange).toHaveBeenCalledWith([{id: 'item1'}, {id: 'item2'}]);
});

it('should invoke onChange on selectAll/deselectAll', () => {
  const node = shallow(<Checklist {...props} />);

  node.find('.selectAll').prop('onChange')?.({
    target: {checked: true},
  } as jest.MockedObject<ChangeEvent<HTMLInputElement>>);

  expect(props.onChange).toHaveBeenCalledWith(props.allItems);

  node.find('.selectAll').prop('onChange')?.({target: {checked: false}} as jest.MockedObject<
    ChangeEvent<HTMLInputElement>
  >);

  expect(props.onChange).toHaveBeenCalledWith([]);
});

it('should hide selectAll if there is only one item', () => {
  props.formatter.mockReturnValueOnce([{id: 'item1', checked: true, label: 'item 1'}]);
  const node = shallow(<Checklist {...props} />);

  expect(node.find('.selectAll')).not.toExist();
});

it('should not highlight disabled items', () => {
  props.formatter.mockReturnValueOnce([
    {id: 'item1', checked: true, label: 'item 1', disabled: true},
  ]);

  const node = shallow(<Checklist {...props} />);

  expect(node.find({label: 'item 1'}).hasClass('highlight')).toBe(false);
});

it('should filter items based on search', () => {
  const node = shallow(<Checklist {...props} />);

  node.find('.searchInput').simulate('change', {target: {value: 'item 1'}});

  expect(node.find({label: 'item 2'})).not.toExist();
});

it('should display the id if the label is null', () => {
  props.formatter.mockReturnValueOnce([
    {id: 'item1', checked: false, label: null, disabled: false},
  ]);

  const node = shallow(<Checklist {...props} />);

  expect(node.find({label: 'item1'})).toExist();
});

it('should select all items in view', () => {
  props.formatter.mockReturnValueOnce([]);
  const node = shallow(<Checklist {...props} />);

  node.find('.searchInput').simulate('change', {target: {value: 'item'}});

  node.find({label: 'Select all in view'}).simulate('change', {target: {checked: true}});

  expect(props.onChange).toHaveBeenCalledWith([{id: 'item1'}, {id: 'item2'}]);
});

it('should hide header if specified', () => {
  const node = shallow(<Checklist {...props} headerHidden />);

  expect(node.find('.cds--header')).not.toExist();
});

it('should prepend items to the checklist', () => {
  const node = shallow(
    <Checklist {...props} preItems={<input className="test" type="checkbox" />} />
  );

  expect(node.find('.itemsList').childAt(0).prop('className')).toBe('test');
});

it('should allow overwriting the selectAll button with a custom header', () => {
  const node = shallow(<Checklist {...props} customHeader="Custom Header Content" />);

  expect(node.find('.selectAll')).not.toExist();
  expect(node.find('.customHeader')).toIncludeText('Custom Header Content');
});
