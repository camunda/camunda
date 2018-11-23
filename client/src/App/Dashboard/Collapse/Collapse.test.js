import React from 'react';
import {shallow} from 'enzyme';
import Collapse from './Collapse';
import * as Styled from './styled';

const mockProps = {
  content: <div data-test="content">content</div>,
  header: <div data-test="header">header</div>,
  buttonTitle: 'someTitle'
};

describe('Collapse', () => {
  it('should display the right data', () => {
    const node = shallow(<Collapse {...mockProps} />);
    const button = node.find(Styled.Button);

    expect(node.find('[data-test="header"]')).toExist();
    expect(node.find('[data-test="content"]')).not.toExist();
    expect(button).toExist();
    expect(button.props().title).toEqual(mockProps.buttonTitle);
  });

  it('should display the content when clicking on the button', () => {
    const node = shallow(<Collapse {...mockProps} />);
    const button = node.find(Styled.Button);

    button.simulate('click');
    expect(node.find('[data-test="content"]')).toExist();

    button.simulate('click');
    expect(node.find('[data-test="content"]')).not.toExist();
  });
});
