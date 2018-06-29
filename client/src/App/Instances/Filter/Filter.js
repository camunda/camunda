import React from 'react';
import PropTypes from 'prop-types';

import Checkbox from 'modules/components/Checkbox';
import {LABELS} from './constants';

import * as Styled from './styled.js';

export default class Filter extends React.Component {
  static propTypes = {
    filter: PropTypes.object.isRequired,
    onChange: PropTypes.func.isRequired,
    type: PropTypes.oneOf(['running', 'finished']).isRequired
  };
  constructor(props) {
    super(props);
    this.filterTypes = Object.keys(props.filter);
  }

  getCheckedChildrenCount = () => {
    return Object.values(this.props.filter).filter(value => value).length;
  };

  getLabel = type => {
    const labels = {
      running: LABELS.RUNNING,
      active: LABELS.ACTIVE,
      incidents: LABELS.INCIDENTS,
      finished: LABELS.FINISHED,
      completed: LABELS.COMPLETED,
      canceled: LABELS.CANCELED
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
    const change = {};
    if (this.getCheckedChildrenCount() === 2) {
      this.filterTypes.map(type =>
        Object.assign(change, {
          [type]: {$set: !this.props.filter[type]}
        })
      );
    } else {
      this.filterTypes.map(type =>
        Object.assign(change, {[type]: {$set: true}})
      );
    }

    this.props.onChange(change);
  };

  render() {
    const {type, filter} = this.props;

    return (
      <Styled.Filters>
        <div>
          <Checkbox
            label={this.getLabel(type)}
            isIndeterminate={this.isIndeterminate()}
            isChecked={this.getCheckedChildrenCount() === 2}
            onChange={this.onResetFilter}
          />
        </div>
        <Styled.NestedFilters>
          {this.filterTypes.map((key, index) => {
            return (
              <div key={index}>
                <Checkbox
                  label={this.getLabel(key)}
                  isChecked={filter[key]}
                  onChange={this.handleChange(key)}
                />
              </div>
            );
          })}
        </Styled.NestedFilters>
      </Styled.Filters>
    );
  }
}
