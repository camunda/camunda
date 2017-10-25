import React from 'react';
import { mount } from 'enzyme';

import Table from './Table';
import {Link} from 'react-router-dom';

jest.mock('react-router-dom', () => { return {
  Link: ({children}) => {return <a>{children}</a>}
}});

it('should render without crashing', () => {
  mount(<Table data={[[]]} />);
});

it('should display a row for each entry in the data array', () => {
  const node = mount(<Table data={[
    ['a', 'b', 'c'],
    ['a', 'b', 'c'],
    ['a', 'b', 'c']
  ]} />);

  expect(node.find('tr')).toHaveLength(3);
});

it('should draw the content of plain text cells', () => {
  const node = mount(<Table data={[['cell']]} />);

  expect(node).toIncludeText('cell');
});

it('should draw a button when an onclick handler is given', () => {
  const spy = jest.fn();
  const node = mount(<Table data={[[{content: 'cell', onClick: spy}]]} />);

  expect(node.find('button')).toBePresent();
  expect(node.find('button')).toIncludeText('cell');
});

it('should call the onclick handler when clicking on the button', () => {
  const spy = jest.fn();
  const node = mount(<Table data={[[{content: 'cell', onClick: spy}]]} />);

  node.find('button').simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should draw a link when providing a link property', () => {
  const node = mount(<Table data={[[{content: 'cell', link: '/newRoute'}]]} />);

  expect(node.find('a')).toBePresent();
  expect(node.find('a')).toIncludeText('cell');
});
