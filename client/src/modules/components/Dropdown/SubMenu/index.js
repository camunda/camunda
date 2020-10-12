/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {ReactComponent as Right} from 'modules/components/Icon/right.svg';

import * as Styled from './styled';

export default class SubMenu extends React.Component {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node,
    ]),
    label: PropTypes.string,
    onStateChange: PropTypes.func,
    isOpen: PropTypes.bool,
    isFixed: PropTypes.bool,
  };

  state = {submenuActive: false, isFocused: false};

  handleOnClick = (evt) => {
    const {isOpen, isFixed} = this.props;

    if (!isOpen) {
      this.props.onStateChange({
        isSubMenuOpen: !isOpen,
        isSubmenuFixed: !isFixed,
      });
      this.setState({submenuActive: !this.state.submenuActive});
    }

    if (isOpen) {
      this.props.onStateChange({
        isSubmenuFixed: !isFixed,
      });
      this.setState({submenuActive: !this.state.submenuActive});
    }

    if (isOpen && isFixed) {
      this.props.onStateChange({
        isSubmenuFixed: !isFixed,
        isSubMenuOpen: !isOpen,
      });
    }
  };

  handleMouseLeave = (evt) => {
    const {isOpen, isFixed} = this.props;

    if (!isFixed && isOpen) {
      this.props.onStateChange({
        isSubMenuOpen: !isOpen,
      });
      this.setState({submenuActive: false});
    }
  };

  handleMenuMouseOver = () => {
    this.setState({submenuActive: true});
  };

  handleButtonMouseOver = (evt) => {
    const {isOpen} = this.props;

    if (!isOpen) {
      this.props.onStateChange({
        isSubMenuOpen: !isOpen,
      });
    }
  };

  render() {
    const {isOpen, children, label} = this.props;
    return (
      <Styled.SubMenu
        onMouseLeave={this.handleMouseLeave}
        data-testid="sub-menu"
      >
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
                  onStateChange: this.props.onStateChange,
                })}
              </Styled.Li>
            ))}
          </Styled.Ul>
        ) : null}
      </Styled.SubMenu>
    );
  }
}
