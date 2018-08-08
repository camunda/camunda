import React from 'react';
import {shallow} from 'enzyme';

import {DROPDOWN_PLACEMENT} from 'modules/constants';

import Menu from './Menu';

describe('DropdownMenu', () => {
  let node, DropdownOption;
  beforeEach(() => {
    DropdownOption = () => <span>I am a Dropdown.Option Component</span>;
    node = shallow(
      <Menu placement={DROPDOWN_PLACEMENT.TOP}>
        <DropdownOption />
      </Menu>
    );
  });

  it('should match snapshot', async () => {
    expect(node).toMatchSnapshot();
  });

  it('should renders its children', () => {
    expect(node.find(DropdownOption)).toExist();
  });
});
