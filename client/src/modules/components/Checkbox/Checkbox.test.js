import React from 'react';
import {shallow} from 'enzyme';

import Checkbox from './Checkbox';

describe('<Checkbox />', () => {
  const mockOnChange = jest.fn();

  it('should update "isChecked" state according to props', () => {
    //given
    let isChecked, node;
    isChecked = true;
    node = shallow(<Checkbox onChange={mockOnChange} isChecked={isChecked} />);
    expect(node.state().isChecked).toBe(true);

    // when
    isChecked = false;
    node.setProps({isChecked});

    //then
    expect(node.state().isChecked).toBe(false);
  });

  it('should toggle "isChecked" state on click', () => {
    // given
    const node = shallow(<Checkbox onChange={mockOnChange} isChecked={true} />);
    // when
    node.find('[data-test-id="checkbox"]').simulate('click');
    // then
    expect(node.state().isChecked).toBe(false);
  });

  it('should display a label if passed as props', () => {
    // given
    const node = shallow(
      <Checkbox label={'foo'} onChange={mockOnChange} isChecked={true} />
    );
    // then
    expect(node.find('[data-test-id="label"]')).toExist();
  });
});
