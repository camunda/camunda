import React from 'react';
import PropTypes from 'prop-types';

import {Down} from 'modules/components/Icon';
import {DROPDOWN_PLACEMENT} from 'modules/constants';

import Menu from './Menu';
import Option from './Option';

import * as Styled from './styled';

export default class Dropdown extends React.Component {
  static propTypes = {
    /** The content that is visible on the dropdown trigger. */
    label: PropTypes.node.isRequired,
    /** Defines if the dropdown content opens to the top or bottom.*/
    placement: PropTypes.oneOf(['top', 'bottom']),
    /** The options of this dropdown. Each child should be a `Dropdown.Option` instance */
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  state = {isOpen: false};

  componentDidMount() {
    document.body.addEventListener('click', this.onClose, true);
  }

  componentWillUnmount() {
    document.body.removeEventListener('click', this.onClose, true);
  }

  setRef = node => {
    this.container = node;
  };

  toggleMenu = () => {
    this.setState({isOpen: !this.state.isOpen});
  };

  onClose = ({target}) => {
    if (!this.container.contains(target)) {
      this.setState({isOpen: false});
    }
  };

  renderLabel = label => {
    return typeof label === 'string' ? (
      <Styled.LabelWrapper>{label}</Styled.LabelWrapper>
    ) : (
      label
    );
  };

  renderChildrenWithProps = () => {
    return React.Children.map(this.props.children, child => {
      return React.cloneElement(child, {
        placement: this.props.placement || DROPDOWN_PLACEMENT.BOTTOM
      });
    });
  };

  render() {
    return (
      <Styled.Dropdown innerRef={this.setRef}>
        <Styled.Button onClick={this.toggleMenu}>
          {this.renderLabel(this.props.label)}
          <Down />
        </Styled.Button>
        {this.state.isOpen && (
          <Menu placement={this.props.placement || DROPDOWN_PLACEMENT.BOTTOM}>
            {this.renderChildrenWithProps()}
          </Menu>
        )}
      </Styled.Dropdown>
    );
  }
}

// export Dropdown-option component
Dropdown.Option = Option;
