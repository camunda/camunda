import React from 'react';
import PropTypes from 'prop-types';

import Checkbox from 'modules/components/Checkbox';

import * as Styled from './styled.js';

export default class Filter extends React.Component {
  static propTypes = {
    filter: PropTypes.object.isRequired,
    onChange: PropTypes.func.isRequired,
    parentType: PropTypes.string.isRequired,
    childTypes: PropTypes.arrayOf(Object).isRequired
  };

  getCheckedChildrenCount = () => {
    return Object.values(this.props.filter).filter(value => value).length;
  };

  getLabel = type => {
    const labels = {
      running: 'Running Instances',
      active: 'Active',
      incidents: 'Incidents',
      finished: 'Completed Instances',
      completed: 'Regularly Completed',
      canceled: 'Cancelled'
    };
    return labels[type];
  };

  isIndeterminate = () => {
    return this.getCheckedChildrenCount() === 1;
  };

  handleChange = type => async () => {
    const {filter} = this.props;
    const change = {
      [type]: {
        $set: filter[type] ? !filter[type] : true
      }
    };

    this.props.onChange(change);
  };

  onResetFilter = () => {
    const {childTypes} = this.props;
    const change = {};

    if (this.getCheckedChildrenCount() === 2) {
      childTypes.map(type =>
        Object.assign(change, {
          [type]: {$set: !this.props.filter[type]}
        })
      );
    } else {
      childTypes.map(type => Object.assign(change, {[type]: {$set: true}}));
    }

    this.props.onChange(change);
  };

  render() {
    const {childTypes, parentType, filter} = this.props;

    return (
      <Styled.Filters>
        <div>
          <Checkbox
            label={this.getLabel(parentType)}
            isIndeterminate={this.isIndeterminate()}
            isChecked={this.getCheckedChildrenCount() === 2}
            onChange={this.onResetFilter}
          />
        </div>
        <Styled.NestedFilters>
          {childTypes.map((child, index) => {
            return (
              <div key={index}>
                <Checkbox
                  label={this.getLabel(child)}
                  isChecked={filter[child]}
                  onChange={this.handleChange(child)}
                />
              </div>
            );
          })}
        </Styled.NestedFilters>
      </Styled.Filters>
    );
  }
}
