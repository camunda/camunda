import React from 'react';
import { mount } from 'enzyme';

import Table from './Table';

jest.mock('react-router-dom', () => { return {
  Link: ({children}) => {return <a>{children}</a>}
}});

jest.mock('components', () => {
  return {
    Button: props => <button {...props}>{props.children}</button>
  }
});

it('should render without crashing', () => {
  mount(<Table {...{head: [], body: [], foot: []}} />);
});

it('shoud display header', () => {
  const node = mount(<Table head={['x', 'y', 'z']} body={[
    ['a', 'b', 'c'],
    ['a', 'b', 'c'],
    ['a', 'b', 'c']
  ]} />);

  expect(node.find('thead')).toBePresent();
});

it('should display a row for each entry in the data array', () => {
  const node = mount(<Table body={[
    ['a', 'b', 'c'],
    ['a', 'b', 'c'],
    ['a', 'b', 'c']
  ]} />);

  expect(node.find('tr')).toHaveLength(3);
});

it('should draw the content of plain text cells', () => {
  const node = mount(<Table body={[['cell']]} />);

  expect(node).toIncludeText('cell');
});

it('should draw a button when an onclick handler is given', () => {
  const spy = jest.fn();
  const node = mount(<Table body={[[{content: 'cell', onClick: spy}]]} />);

  expect(node.find('button')).toBePresent();
  expect(node.find('button')).toIncludeText('cell');
});

it('should call the onclick handler when clicking on the button', () => {
  const spy = jest.fn();
  const node = mount(<Table body={[[{content: 'cell', onClick: spy}]]} />);

  node.find('button').simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should draw a link when providing a link property', () => {
  const node = mount(<Table body={[[{content: 'cell', link: '/newRoute'}]]} />);

  expect(node.find('a')).toBePresent();
  expect(node.find('a')).toIncludeText('cell');
});

it('should render apply the className to buttons', () => {
  const node = mount(<Table body={[[{content: 'cell', onClick: jest.fn(), className: 'buttonClass'}]]} />);

  expect(node.find('.buttonClass')).toBePresent();
});

it('should render apply the className to links', () => {
  const node = mount(<Table body={[[{content: 'cell', link: '/newRoute', className: 'linkClass'}]]} />);

  expect(node.find('.linkClass')).toBePresent();
});

it('should apply custom classNames for the Table', () => {
  const node = mount(<Table className='myCustomClass' body={[[]]} />);

  expect(node.getDOMNode().classList.contains('myCustomClass')).toBe(true);
});

it('should render predefined react components', () => {
  const node = mount(<Table body={[[<p>Hello world</p>]]} />);

  expect(node).toIncludeText('Hello world');
});

it('should not crash when providing an unrecognized object as cell content', () => {
  mount(<Table body={[[{foo: 'bar'}]]} />)
});
