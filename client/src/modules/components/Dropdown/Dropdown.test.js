import React from 'react';
import { mount } from 'enzyme';

import Dropdown from './Dropdown';

jest.mock('components', () => {return {
  Button: ({children}) => <button>{children}</button>
}});

jest.mock('./DropdownOption', () => {return (props) => {
    return <button className='DropdownOption'>{props.children}</button>;
  }
});

it('should render without crashing', () => {
  mount(<Dropdown />);
});

it('should contain the specified label', () => {
  const node = mount(<Dropdown label='Click me' />);

  expect(node).toIncludeText('Click me');
})

it('should display the child elements when clicking the trigger', () => {
  const node = mount(<Dropdown label='Click me'>
    <Dropdown.Option>foo</Dropdown.Option>
  </Dropdown>);

  node.find('.Dropdown__button').simulate('click');

  expect(node.find('.Dropdown')).toMatchSelector('.is-open');
})

it('should close when clicking somewhere', () => {
  const node = mount(<Dropdown label='Click me'>
    <Dropdown.Option>foo</Dropdown.Option>
  </Dropdown>);

  node.setState({open: true});

  node.simulate('click');

  expect(node.state('open')).toBe(false);
  expect(node.find('.Dropdown')).not.toMatchSelector('.is-open');
})

it('should close when selecting an option', () => {
  const node = mount(<Dropdown label='Click me'>
    <Dropdown.Option><p className='test_option'>foo</p></Dropdown.Option>
  </Dropdown>);

  node.setState({open: true});

  node.find('.test_option').simulate('click');

  expect(node.state('open')).toBe(false);
  expect(node.find('.Dropdown')).not.toMatchSelector('.is-open');
});

it('should set aria-haspopup to true', () => {
  const node = mount(<Dropdown label='Click me'>
    <Dropdown.Option>foo</Dropdown.Option>
  </Dropdown>);

  expect(node.find('.Dropdown__button')).toMatchSelector('.Dropdown__button[aria-haspopup="true"]');
})

it('should set aria-expanded to false by default', () => {
  const node = mount(<Dropdown label='Click me'>
    <Dropdown.Option>foo</Dropdown.Option>
  </Dropdown>);

  expect(node.find('.Dropdown__button')).toMatchSelector('.Dropdown__button[aria-expanded="false"]');
});

it('should set aria-expanded to true when open', () => {
  const node = mount(<Dropdown label='Click me'>
    <Dropdown.Option>foo</Dropdown.Option>
  </Dropdown>);

  node.simulate('click');

  expect(node.state('open')).toBe(true);
  expect(node.find('.Dropdown__button')).toMatchSelector('.Dropdown__button[aria-expanded="true"]');
})

it('should set aria-expanded to false when closed', () => {
  const node = mount(<Dropdown label='Click me'>
    <Dropdown.Option>foo</Dropdown.Option>
  </Dropdown>);

  node.setState({open: true});

  node.simulate('click');

  expect(node.state('open')).toBe(false);
  expect(node.find('.Dropdown__button')).toMatchSelector('.Dropdown__button[aria-expanded="false"]');
})

it('should set aria-labelledby on the menu as provided as a prop, amended by "-button"', () => {
  const node = mount(<Dropdown id='my-dropdown'>
    <Dropdown.Option>foo</Dropdown.Option>
  </Dropdown>);

  expect(node.find('.Dropdown__menu')).toMatchSelector('.Dropdown__menu[aria-labelledby="my-dropdown-button"]');
})

it('should close after pressing Esc', () => {
  const node = mount(<Dropdown label='Click me'>
    <Dropdown.Option>foo</Dropdown.Option>
    <Dropdown.Option>bar</Dropdown.Option>
  </Dropdown>);

  node.setState({open: true});

  node.simulate('keyDown', {key: 'Escape', keyCode: 27, which: 27});

  expect(node.state('open')).toBe(false);
});

it('should not change focus after pressing an arrow key if closed', () => {

  const node = mount(<Dropdown label='Click me'>
    <Dropdown.Option>foo</Dropdown.Option>
    <Dropdown.Option>bar</Dropdown.Option>
  </Dropdown>);

  node.find('button').first().getDOMNode().focus();

  node.simulate('keyDown', {key: 'ArrowDown'});
  expect(document.activeElement.textContent).toBe("Click me");
});

it('should change focus after pressing an arrow key if opened', () => {

  const node = mount(<Dropdown label='Click me'>
    <Dropdown.Option>foo</Dropdown.Option>
    <Dropdown.Option>bar</Dropdown.Option>
  </Dropdown>);

  node.find('button').first().getDOMNode().focus();

  node.instance().setState({open: true});

  node.simulate('keyDown', {key: 'ArrowDown'});
  expect(document.activeElement.textContent).toBe("foo");
  node.simulate('keyDown', {key: 'ArrowDown'});
  expect(document.activeElement.textContent).toBe("bar");
});
