/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import * as Styled from './styled';

type Props = {
  isChecked?: boolean;
  disabled?: boolean;
  onChange: (...args: any[]) => any;
  isIndeterminate?: boolean;
  label?: string;
  name?: string;
  type?: 'selection';
  value?: string;
  title?: string;
  id?: string;
};

type State = any;

export default class Checkbox extends React.Component<Props, State> {
  el: any;

  constructor(props: Props) {
    super(props);
    this.el = {};
    this.state = {
      isFocused: false,
    };
  }

  componentDidMount() {
    const {isIndeterminate} = this.props;

    if (isIndeterminate) {
      this.el.indeterminate = isIndeterminate;
    }
  }

  componentDidUpdate(prevProps: Props) {
    const {isIndeterminate} = this.props;

    if (prevProps.isIndeterminate !== isIndeterminate) {
      this.el.indeterminate = isIndeterminate;
    }
  }

  handleChange = (event: any) => {
    this.props.onChange(event, event.target.checked);
  };

  handleFocus = (event: any) => {
    const {isFocused} = this.state;
    this.setState({isFocused: !isFocused});
  };

  inputRef = (node: any) => {
    this.el = node;
  };

  render() {
    const {
      id,
      label,
      onChange,
      isIndeterminate,
      isChecked,
      type,
      title,
      ...other
    } = this.props;
    const {isFocused} = this.state;
    return (
      <Styled.Checkbox>
        <Styled.CustomCheckbox
          checkboxType={type}
          checked={isChecked}
          indeterminate={isIndeterminate}
          focused={isFocused}
        />

        <Styled.Input
          data-testid="checkbox-input"
          id={id}
          name={id}
          type="checkbox"
          checked={isChecked}
          ref={this.inputRef}
          onChange={this.handleChange}
          onFocus={this.handleFocus}
          onBlur={this.handleFocus}
          aria-label={!id ? label || title : undefined}
          title={label || title}
          {...other}
        />

        {label && (
          <Styled.Label checked={isChecked || isIndeterminate} htmlFor={id}>
            {label}
          </Styled.Label>
        )}
      </Styled.Checkbox>
    );
  }
}
