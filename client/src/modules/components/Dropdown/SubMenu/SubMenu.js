import React from 'react';
import PropTypes from 'prop-types';

import {Right} from 'modules/components/Icon';

import * as Styled from './styled';

export default class SubMenu extends React.Component {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ]),
    label: PropTypes.string,
    onStateChange: PropTypes.func,
    isOpen: PropTypes.bool,
    isFixed: PropTypes.bool
  };

  state = {submenuActive: false, isFocused: false};

  handleOnClick = evt => {
    const {isOpen, isFixed} = this.props;

    if (!isOpen) {
      this.props.onStateChange({
        isSubMenuOpen: !isOpen,
        isSubmenuFixed: !isFixed
      });
      this.setState({submenuActive: !this.state.submenuActive});
    }

    if (isOpen) {
      this.props.onStateChange({
        isSubmenuFixed: !isFixed
      });
      this.setState({submenuActive: !this.state.submenuActive});
    }

    if (isOpen && isFixed) {
      this.props.onStateChange({
        isSubmenuFixed: !isFixed,
        isSubMenuOpen: !isOpen
      });
    }
  };

  handleMouseLeave = evt => {
    const {isOpen, isFixed} = this.props;

    if (!isFixed && isOpen) {
      this.props.onStateChange({
        isSubMenuOpen: !isOpen
      });
      this.setState({submenuActive: false});
    }
  };

  handleMenuMouseOver = () => {
    this.setState({submenuActive: true});
  };

  handleButtonMouseOver = evt => {
    const {isOpen} = this.props;

    if (!isOpen) {
      this.props.onStateChange({
        isSubMenuOpen: !isOpen
      });
    }
  };

  render() {
    const {isOpen, children, label} = this.props;
    return (
      <Styled.SubMenu onMouseLeave={this.handleMouseLeave}>
        <Styled.SubMenuButton
          onClick={() => this.handleOnClick()}
          onMouseOver={this.handleButtonMouseOver}
          submenuActive={this.state.submenuActive}
        >
          <span>{label}</span>
          <Right />
        </Styled.SubMenuButton>
        {isOpen ? (
          <Styled.Ul>
            {React.Children.map(children, (child, index) => (
              <Styled.Li onMouseOver={this.handleMenuMouseOver} key={index}>
                {React.cloneElement(child, {
                  onStateChange: this.props.onStateChange
                })}
              </Styled.Li>
            ))}
          </Styled.Ul>
        ) : null}
      </Styled.SubMenu>
    );
  }
}
