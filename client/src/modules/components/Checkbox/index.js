/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

export default class Checkbox extends React.Component {
  static propTypes = {
    isChecked: PropTypes.bool,
    onChange: PropTypes.func.isRequired,
    isIndeterminate: PropTypes.bool,
    label: PropTypes.string,
    name: PropTypes.string,
    type: PropTypes.oneOf(['selection']),
    value: PropTypes.string,
    title: PropTypes.string,
    id: PropTypes.string,
  };

  constructor(props) {
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

  componentDidUpdate(prevProps) {
    const {isIndeterminate} = this.props;

    if (prevProps.isIndeterminate !== isIndeterminate) {
      this.el.indeterminate = isIndeterminate;
    }
  }

  handleChange = (event) => {
    this.props.onChange(event, event.target.checked);
  };

  handleFocus = (event) => {
    const {isFocused} = this.state;
    this.setState({isFocused: !isFocused});
  };

  inputRef = (node) => {
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
          data-test="checkbox-input"
          id={id}
          indeterminate={isIndeterminate}
          type="checkbox"
          checked={isChecked}
          ref={this.inputRef}
          checkboxType={type}
          onChange={this.handleChange}
          onFocus={this.handleFocus}
          onBlur={this.handleFocus}
          aria-label={!id ? label || title : null}
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
