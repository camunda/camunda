/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import Checkbox from 'modules/components/Checkbox';
import {INSTANCES_LABELS} from 'modules/constants';

import * as Styled from './styled.js';

export default class CheckboxGroup extends React.Component {
  static propTypes = {
    filter: PropTypes.shape({
      active: PropTypes.bool,
      incidents: PropTypes.bool,
      canceled: PropTypes.bool,
      completed: PropTypes.bool,
    }).isRequired,
    onChange: PropTypes.func.isRequired,
    type: PropTypes.oneOf(['running', 'finished']).isRequired,
  };
  constructor(props) {
    super(props);
    this.childFilterTypes = Object.keys(props.filter);
  }

  getCheckedChildrenCount = () => {
    return Object.values(this.props.filter).filter((value) => value).length;
  };

  areAllChildrenChecked = () => {
    const childrenCount = this.getCheckedChildrenCount();
    return this.childFilterTypes.length === childrenCount;
  };

  isIndeterminate = () => {
    const childrenCount = this.getCheckedChildrenCount();
    return childrenCount >= 1 && childrenCount < this.childFilterTypes.length;
  };

  handleChange = (type) => (event, isChecked) => {
    this.props.onChange({[type]: isChecked});
  };

  onResetFilter = () => {
    const change = {};
    const allChildrenChecked = this.areAllChildrenChecked();

    this.childFilterTypes.map((type) =>
      Object.assign(change, {
        [type]: !allChildrenChecked,
      })
    );

    this.props.onChange(change);
  };

  render() {
    const {type, filter, onChange: _, ...props} = this.props;

    return (
      <Styled.CheckboxGroup {...props}>
        <div>
          <Checkbox
            id={type}
            label={INSTANCES_LABELS[type]}
            isIndeterminate={this.isIndeterminate()}
            isChecked={this.areAllChildrenChecked()}
            onChange={this.onResetFilter}
          />
        </div>
        <Styled.NestedCheckboxes>
          {this.childFilterTypes.map((type) => {
            return (
              <Checkbox
                key={type}
                id={type}
                label={INSTANCES_LABELS[type]}
                isChecked={filter[type] || false}
                onChange={this.handleChange(type)}
              />
            );
          })}
        </Styled.NestedCheckboxes>
      </Styled.CheckboxGroup>
    );
  }
}
