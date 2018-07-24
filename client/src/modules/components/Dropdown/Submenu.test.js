import React from 'react';
import {mount} from 'enzyme';

import Submenu from './Submenu';

jest.mock('./DropdownOption', () => {
  return props => {
    return <button className="DropdownOption">{props.children}</button>;
  };
});

it('should render a dropdown option with the provided label', () => {
  const node = mount(<Submenu label="my label" />);

  expect(node.find('.DropdownOption')).toBePresent();
  expect(node.find('.DropdownOption')).toIncludeText('my label');
});

// for some reason, enzyme does not find the second child of a react fragment.
// check again once https://github.com/airbnb/enzyme/issues/1213 is done
it.skip('should render its child content when it is open', () => {
  const node = mount(<Submenu label="my label">SubmenuContent</Submenu>);

  expect(node).not.toIncludeText('SubmenuContent');

  node.setProps({open: true});

  expect(node).toIncludeText('SubmenuContent');
});
