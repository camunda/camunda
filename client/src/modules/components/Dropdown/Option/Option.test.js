import React from 'react';
import {shallow} from 'enzyme';

import Option from './Option';
import Dropdown from '../Dropdown';

import * as Styled from './styled';

describe('Option', () => {
  let node;
  let Child;
  let onClickMock;

  beforeEach(() => {
    Child = () => <span>I am a label</span>;
    onClickMock = jest.fn();

    node = shallow(
      <Option onClick={onClickMock}>
        <Child />
      </Option>
    );
  });

  it('should render a button if no children are passed', () => {
    node = shallow(<Option onClick={onClickMock} />);
    expect(node.find(Styled.OptionButton)).toExist();
  });

  it('should render passed children', () => {
    expect(node.find(Child)).toExist();
  });

  it('should render sub menu with props', () => {
    node = shallow(
      <Option
        isSubMenuOpen={true}
        isSubmenuFixed={false}
        onStateChange={jest.fn()}
        onClick={onClickMock}
      >
        <Dropdown.SubMenu onStateChange={jest.fn()}>
          <Dropdown.SubOption>'foo'</Dropdown.SubOption>
        </Dropdown.SubMenu>
      </Option>
    );

    const SubMenuProps = node.find(Dropdown.SubMenu).props();

    expect(SubMenuProps.isOpen).toBe(true);
    expect(SubMenuProps.isFixed).toBe(false);
  });

  it('should handle on click event', () => {
    node = shallow(<Option onClick={onClickMock} />);

    const clickSpy = jest.spyOn(node.instance(), 'handleOnClick');
    node.setProps({disabled: false});
    node.find(Styled.Option).simulate('click');

    expect(clickSpy).toHaveBeenCalled();
    expect(onClickMock).toHaveBeenCalled();
  });
});
