/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {ReactComponent as Right} from 'modules/components/Icon/right.svg';

import * as Styled from './styled';

type Props = {
  label?: string;
  onStateChange?: (...args: any[]) => any;
  isOpen?: boolean;
  isFixed?: boolean;
};

type State = any;

export default class SubMenu extends React.Component<Props, State> {
  state = {submenuActive: false, isFocused: false};

  handleOnClick = (evt: any) => {
    const {isOpen, isFixed} = this.props;

    if (!isOpen) {
      // @ts-expect-error ts-migrate(2722) FIXME: Cannot invoke an object which is possibly 'undefin... Remove this comment to see the full error message
      this.props.onStateChange({
        isSubMenuOpen: !isOpen,
        isSubmenuFixed: !isFixed,
      });
      this.setState({submenuActive: !this.state.submenuActive});
    }

    if (isOpen) {
      // @ts-expect-error ts-migrate(2722) FIXME: Cannot invoke an object which is possibly 'undefin... Remove this comment to see the full error message
      this.props.onStateChange({
        isSubmenuFixed: !isFixed,
      });
      this.setState({submenuActive: !this.state.submenuActive});
    }

    if (isOpen && isFixed) {
      // @ts-expect-error ts-migrate(2722) FIXME: Cannot invoke an object which is possibly 'undefin... Remove this comment to see the full error message
      this.props.onStateChange({
        isSubmenuFixed: !isFixed,
        isSubMenuOpen: !isOpen,
      });
    }
  };

  handleMouseLeave = (evt: any) => {
    const {isOpen, isFixed} = this.props;

    if (!isFixed && isOpen) {
      // @ts-expect-error ts-migrate(2722) FIXME: Cannot invoke an object which is possibly 'undefin... Remove this comment to see the full error message
      this.props.onStateChange({
        isSubMenuOpen: !isOpen,
      });
      this.setState({submenuActive: false});
    }
  };

  handleMenuMouseOver = () => {
    this.setState({submenuActive: true});
  };

  handleButtonMouseOver = (evt: any) => {
    const {isOpen} = this.props;

    if (!isOpen) {
      // @ts-expect-error ts-migrate(2722) FIXME: Cannot invoke an object which is possibly 'undefin... Remove this comment to see the full error message
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
          // @ts-expect-error ts-migrate(2554) FIXME: Expected 1 arguments, but got 0.
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
                {/* @ts-expect-error ts-migrate(2769) FIXME: Type 'undefined' is not assignable to type 'ReactE... Remove this comment to see the full error message */}
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
