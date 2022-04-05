/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import * as Styled from './styled';

type Props = {
  label?: React.ReactNode;
  disabled?: boolean;
  onStateChange?: (...args: any[]) => any;
  onClick?: (...args: any[]) => any;
  className?: string;
};

export default class Option extends React.Component<Props> {
  handleOnClick = () => {
    if (!this.props.disabled && this.props.onClick) {
      this.props.onClick();
      // @ts-expect-error ts-migrate(2722) FIXME: Cannot invoke an object which is possibly 'undefin... Remove this comment to see the full error message
      this.props.onStateChange({isOpen: false});
    }
  };

  renderChildrenProps = () =>
    React.Children.map(this.props.children, (child) =>
      // @ts-expect-error ts-migrate(2769) FIXME: Type 'undefined' is not assignable to type 'ReactE... Remove this comment to see the full error message
      React.cloneElement(child, {
        onStateChange: this.props.onStateChange,
      })
    );

  render() {
    const {children, disabled, label, className} = this.props;

    return (
      <Styled.Option
        // @ts-expect-error ts-migrate(2769) FIXME: Property 'label' does not exist on type 'Intrinsic... Remove this comment to see the full error message
        label={label}
        disabled={disabled}
        onClick={this.handleOnClick}
        className={className}
      >
        {!children ? (
          <Styled.OptionButton disabled={disabled}>{label}</Styled.OptionButton>
        ) : (
          this.renderChildrenProps()
        )}
      </Styled.Option>
    );
  }
}
