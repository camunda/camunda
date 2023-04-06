/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow, mount} from 'enzyme';

import {Icon} from 'components';
import {getScreenBounds} from 'services';

import Submenu from './Submenu';
import DropdownOption from './DropdownOption';
import {findLetterOption} from './service';

jest.mock('./DropdownOption', () => {
  return (props: any) => {
    return (
      <div tabIndex={0} className="DropdownOption">
        {props.children}
      </div>
    );
  };
});

jest.mock('./service', () => ({findLetterOption: jest.fn()}));

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  getScreenBounds: jest.fn().mockReturnValue({top: 0, bottom: 100}),
}));

console.error = jest.fn();

it('should render the provided label', () => {
  const node = shallow(<Submenu label="my label" />);

  expect(node.children().at(0)).toIncludeText('my label');
});

it('should change focus after pressing an arrow key', () => {
  const node = mount(
    <Submenu label="Click me">
      <DropdownOption>foo</DropdownOption>
      <DropdownOption>bar</DropdownOption>
    </Submenu>,
    {
      attachTo: document.body,
    }
  );

  node.setProps({open: true});

  const container = node.find('.childrenContainer');

  container.find(DropdownOption).first().getDOMNode<HTMLElement>()?.focus();

  container.simulate('keyDown', {key: 'ArrowDown'});
  expect(document.activeElement?.textContent).toBe('bar');
  container.simulate('keyDown', {key: 'ArrowUp'});
  expect(document.activeElement?.textContent).toBe('foo');
});

it('should open/close the submenu on mouseOver/mouseLeave', () => {
  const openSpy = jest.fn();
  const closeSpy = jest.fn();
  const node = shallow(
    <Submenu label="Click me" setClosed={closeSpy} setOpened={openSpy}>
      <DropdownOption>foo</DropdownOption>
      <DropdownOption>bar</DropdownOption>
    </Submenu>
  );

  node.simulate('mouseover');
  expect(openSpy).toHaveBeenCalled();

  node.simulate('mouseleave');
  expect(closeSpy).toHaveBeenCalled();
});

it('should close the submenu when left arrow is pressed', () => {
  const spy = jest.fn();
  const node = shallow(
    <Submenu label="Click me" forceToggle={spy} open>
      <DropdownOption>foo</DropdownOption>
      <DropdownOption>bar</DropdownOption>
    </Submenu>
  );

  const container = node.find('.childrenContainer');
  container.simulate('keyDown', {
    key: 'ArrowLeft',
    stopPropagation: jest.fn(),
    preventDefault: jest.fn(),
  });

  expect(spy).toHaveBeenCalled();
});

it('should open the submenu when right arrow is pressed', () => {
  const spy = jest.fn();
  const node = shallow(
    <Submenu label="label" forceToggle={spy}>
      <DropdownOption>foo</DropdownOption>
    </Submenu>
  );

  node.simulate('keyDown', {key: 'ArrowRight'});

  expect(spy).toHaveBeenCalled();
});

it('should shift the submenu up when there is no space available', () => {
  (getScreenBounds as jest.Mock).mockReturnValueOnce({top: 10, bottom: 100});
  const node = mount<Submenu>(<Submenu label="label" />);

  node.instance().containerRef = {
    current: {
      // submenu dimensions
      querySelector: () => ({
        clientWidth: 40,
        clientHeight: 60,
      }),
      //parentMenu.top
      getBoundingClientRect: () => ({top: 50} as DOMRect),
    } as unknown as HTMLDivElement,
  };

  node.instance().calculatePlacement();
  node.update();
  expect(node.state().styles.top).toBe('-20px');
});

it('should invoke findLetterOption when typing a character', () => {
  const node = shallow<Submenu>(<Submenu label="label" open={true} />);

  node.instance().containerRef = {
    current: {
      querySelectorAll: () => [],
    } as unknown as HTMLDivElement,
  };

  const container = node.find('.childrenContainer');

  container.simulate('keyDown', {
    key: 'f',
    keyCode: 70,
    stopPropagation: jest.fn(),
    preventDefault: jest.fn(),
  });
  expect((findLetterOption as jest.Mock).mock.calls[0][1]).toBe('f');
  expect((findLetterOption as jest.Mock).mock.calls[0][2]).toBe(0);
});

it('should invoke onClose when closing the submenu', () => {
  const spy = jest.fn();
  const node = shallow(<Submenu label="label" onClose={spy} open />);

  node.setProps({open: false});

  expect(spy).toHaveBeenCalled();
});

it('should open the submenu to left if specified', () => {
  jest
    .spyOn<Element, 'parentNode', 'get', HTMLElement | null>(
      document.activeElement!,
      'parentNode',
      'get'
    )
    .mockReturnValueOnce({
      closest: () => ({focus: jest.fn()}),
    } as unknown as HTMLElement);
  const spy = jest.fn();
  const node = shallow(
    <Submenu label="my label" forceToggle={spy} open openToLeft>
      <DropdownOption>foo</DropdownOption>
    </Submenu>
  );

  expect(node.prop('className').includes('leftCheckMark')).toBe(true);
  expect(node.find(Icon).prop('type')).toBe('left');

  node.simulate('keyDown', {key: 'ArrowLeft'});
  expect(spy).toHaveBeenCalled();
  spy.mockClear();

  const container = node.find('.childrenContainer');
  container.simulate('keyDown', {
    key: 'ArrowRight',
    stopPropagation: jest.fn(),
    preventDefault: jest.fn(),
  });

  expect(spy).toHaveBeenCalled();
});

it('should call onClick if not disabled', () => {
  const spy = jest.fn();
  const node = shallow(<Submenu label="label" onClick={spy} />);

  node.find(DropdownOption).simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should exit onClick if disabled', () => {
  const spy = jest.fn();
  const node = shallow(<Submenu label="label" disabled onClick={spy} />);

  node.find(DropdownOption).simulate('click');

  expect(spy).not.toHaveBeenCalled();
});

it('should exit onMouseOver if disabled', () => {
  const spy = jest.fn();
  const node = shallow(<Submenu label="label" disabled onOpen={spy} />);

  node.find(DropdownOption).simulate('mouseover');

  expect(spy).not.toHaveBeenCalled();
});

it('should exit onMouseLeave if disabled', () => {
  const spy = jest.fn();
  const node = shallow(<Submenu label="label" disabled setClosed={spy} />);

  node.find(DropdownOption).simulate('mouseleave');

  expect(spy).not.toHaveBeenCalled();
});
