/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {CSSTransition} from 'react-transition-group';

import {ReactComponent as Down} from 'modules/components/Icon/down.svg';

import * as Styled from './styled';

type Props = {
  label: React.ReactNode;
  placement?: 'top' | 'bottom';
  buttonStyles?: (...args: any[]) => any;
  disabled?: boolean;
  onOpen?: (...args: any[]) => any;
  className?: string;
  calculateWidth?: (...args: any[]) => any;
  children?: React.ReactNode;
};

type State = {
  isOpen: boolean;
  isFocused: boolean;
};

export default class Dropdown extends React.Component<Props, State> {
  static defaultProps: Partial<Props> = {
    placement: 'bottom',
  };

  container: any;

  state = {
    isOpen: false,
    isFocused: false,
  };

  componentDidMount() {
    document.body.addEventListener('click', this.onClose, true);
  }

  componentWillUnmount() {
    document.body.removeEventListener('click', this.onClose, true);
  }

  handleKeyPress = (evt: any) => {
    //The space bar is interpreted as empty evt.key by react;
    if (evt && evt.key !== 'Tab' && evt.key !== 'Enter' && evt.key !== '') {
      evt.preventDefault();
    }

    if (evt && evt.key === 'Escape') {
      this.onClose({});
    }
  };

  setRef = (node: any) => {
    this.container = node;
  };

  handleStateChange = (changes: any) => {
    this.setState(changes);
  };

  resetState = () => {
    this.setState({
      isOpen: false,
    });
  };

  onClose = ({target}: any) => {
    if (!this.container || !this.container.contains(target) || !target) {
      this.resetState();
    }
  };

  handleOnClick = () => {
    if (this.props.calculateWidth && this.container) {
      this.props.calculateWidth(this.container.clientWidth);
    }

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
        // @ts-expect-error ts-migrate(2769) FIXME: Type 'string' is not assignable to type 'ReactElem... Remove this comment to see the full error message
        React.cloneElement(child, {
          onStateChange: this.handleStateChange,
          placement: this.props.placement,
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
      exit: 20,
    };
    return (
      <Styled.Dropdown ref={this.setRef} className={this.props.className}>
        <Styled.Button
          data-testid="dropdown-toggle"
          onKeyDown={this.handleKeyPress}
          // @ts-expect-error ts-migrate(2769) FIXME: Property 'buttonStyles' does not exist on type 'In... Remove this comment to see the full error message
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
            $transitionTiming={transitionTiming}
            onKeyDown={this.handleKeyPress}
            placement={placement}
          >
            {this.renderChildrenWithProps()}
          </Styled.MenuComponent>
        </CSSTransition>
      </Styled.Dropdown>
    );
  }
}
