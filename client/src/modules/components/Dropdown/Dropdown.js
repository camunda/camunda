/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {CSSTransition} from 'react-transition-group';

import {ReactComponent as Down} from 'modules/components/Icon/down.svg';

import {DROPDOWN_PLACEMENT} from 'modules/constants';

import SubMenu from './SubMenu';
import Option from './Option';
import SubOption from './SubOption';

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
    ]),
    buttonStyles: PropTypes.array,
    disabled: PropTypes.bool,
    onOpen: PropTypes.func,
    className: PropTypes.string
  };

  state = {
    isOpen: false,
    isSubMenuOpen: false,
    isSubmenuFixed: false,
    isFocused: false
  };

  componentDidMount() {
    document.body.addEventListener('click', this.onClose, true);
  }

  componentWillUnmount() {
    document.body.removeEventListener('click', this.onClose, true);
  }

  handleKeyPress = evt => {
    //The space bar is interpreted as empty evt.key by react;
    if (evt && evt.key !== 'Tab' && evt.key !== 'Enter' && evt.key !== '') {
      evt.preventDefault();
    }

    if (evt && evt.key === 'Escape') {
      this.onClose({});
    }
  };

  setRef = node => {
    this.container = node;
  };

  handleStateChange = changes => {
    this.setState(changes);
  };

  resetState = () => {
    this.setState({
      isOpen: false,
      isSubMenuOpen: false,
      isSubmenuFixed: false
    });
  };

  onClose = ({target}) => {
    if (!this.container || !this.container.contains(target) || !target) {
      this.resetState();
    }
  };

  handleOnClick = () => {
    if (!this.state.isOpen && this.props.onOpen) {
      this.props.onOpen();
    }

    !this.state.isOpen
      ? this.setState({isOpen: !this.state.isOpen})
      : this.resetState();
  };

  renderChildrenWithProps = () =>
    React.Children.map(this.props.children, (child, index) => {
      return (
        child != null &&
        React.cloneElement(child, {
          isSubMenuOpen: this.state.isSubMenuOpen,
          isSubmenuFixed: this.state.isSubmenuFixed,
          onStateChange: this.handleStateChange,
          placement: this.props.placement
        })
      );
    });

  renderLabel = () =>
    typeof this.props.label === 'string' ? (
      <Styled.LabelWrapper>{this.props.label}</Styled.LabelWrapper>
    ) : (
      this.props.label
    );

  render() {
    const {isOpen} = this.state;
    const {placement} = this.props;
    const transitionTiming = {
      enter: 50,
      exit: 20
    };
    return (
      <Styled.Dropdown ref={this.setRef} className={this.props.className}>
        <Styled.Button
          data-test="dropdown-toggle"
          onKeyDown={this.handleKeyPress}
          buttonStyles={this.props.buttonStyles}
          disabled={this.props.disabled}
          onClick={() => this.handleOnClick()}
          data-button-open={isOpen}
        >
          {this.renderLabel()}
          <Down />
        </Styled.Button>

        <CSSTransition
          in={isOpen}
          mountOnEnter
          unmountOnExit
          classNames="transition"
          timeout={transitionTiming}
        >
          <Styled.MenuComponent
            transitionTiming={transitionTiming}
            onKeyDown={this.handleKeyPress}
            onStateChange={this.handleStateChange}
            placement={placement}
          >
            {this.renderChildrenWithProps()}
          </Styled.MenuComponent>
        </CSSTransition>
      </Styled.Dropdown>
    );
  }
}

Dropdown.defaultProps = {
  placement: DROPDOWN_PLACEMENT.BOTTOM
};

// export Dropdown-option component
Dropdown.Option = Option;
Dropdown.SubMenu = SubMenu;
Dropdown.SubOption = SubOption;
