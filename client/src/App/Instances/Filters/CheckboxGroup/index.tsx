/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import Checkbox from 'modules/components/Checkbox';
import {INSTANCES_LABELS} from 'modules/constants';

import * as Styled from './styled';

type Props = {
  filter: {
    active?: boolean;
    incidents?: boolean;
    canceled?: boolean;
    completed?: boolean;
  };
  onChange: (...args: any[]) => any;
  type: 'running' | 'finished';
};

export default class CheckboxGroup extends React.Component<Props> {
  childFilterTypes: any;
  constructor(props: Props) {
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

  handleChange = (type: any) => (event: any, isChecked: any) => {
    this.props.onChange({[type]: isChecked});
  };

  onResetFilter = () => {
    const change = {};
    const allChildrenChecked = this.areAllChildrenChecked();

    this.childFilterTypes.map((type: any) =>
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
          {this.childFilterTypes.map((type: any) => {
            return (
              <Checkbox
                key={type}
                id={type}
                // @ts-expect-error ts-migrate(7053) FIXME: Element implicitly has an 'any' type because expre... Remove this comment to see the full error message
                label={INSTANCES_LABELS[type]}
                // @ts-expect-error ts-migrate(7053) FIXME: Element implicitly has an 'any' type because expre... Remove this comment to see the full error message
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
