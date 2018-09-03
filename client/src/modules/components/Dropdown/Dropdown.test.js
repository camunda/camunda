import React from 'react';
import {shallow} from 'enzyme';

import {Batch} from 'modules/components/Icon';
import {DROPDOWN_PLACEMENT} from 'modules/constants';

import Dropdown from './Dropdown';

import * as Styled from './styled';

const buttonStyles = {
  fontSize: '13px'
};

const stringLabel = 'Some Label';

describe('Dropdown', () => {
  let node;

  beforeEach(() => {
    node = shallow(
      <Dropdown
        placement={DROPDOWN_PLACEMENT.TOP}
        label={stringLabel}
        buttonStyle={buttonStyles}
      >
        <Dropdown.Option
          disabled={false}
          onClick={jest.fn()}
          label="Create New Selection"
        />
      </Dropdown>
    );
  });

  it('should be closed initially', () => {
    expect(node.state().isOpen).toBe(false);
  });

  it('should show/hide child contents when isOpen/closed', () => {
    //given
    expect(node.find(Dropdown.Option)).not.toExist();

    //when
    node.setState({isOpen: true});

    //then
    expect(node.find(Dropdown.Option)).toExist();
  });

  it('should render string label', () => {
    const label = node.find(Styled.LabelWrapper);
    expect(label.contains(stringLabel));
  });

  it('should render icon label', () => {
    node.setProps({label: <Batch />});
    expect(node.find(Styled.Button).contains(<Batch />));
  });

  it('should pass "bottom" as default placement', () => {
    node = shallow(<Dropdown label={stringLabel} buttonStyle={buttonStyles} />);
    expect(node.instance().props.placement).toBe(DROPDOWN_PLACEMENT.BOTTOM);
  });

  it('should isOpen/close on click of the button', () => {
    //given
    node.find(Styled.Button).simulate('click');
    expect(node.state().isOpen).toBe(true);
    //when
    node.find(Styled.Button).simulate('click');
    //then
    expect(node.state().isOpen).toBe(false);
  });

  it('should close the dropdown when clicking anywhere', async () => {
    //given
    const onCloseSpy = jest.spyOn(node.instance(), 'onClose');
    await node.instance().componentDidMount();
    //when
    document.body.click();
    //then
    expect(onCloseSpy).toHaveBeenCalled();
  });
});
