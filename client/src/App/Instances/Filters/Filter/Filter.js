import React from 'react';
import PropTypes from 'prop-types';

import Checkbox from 'modules/components/Checkbox';
import {LABELS} from 'modules/constants/filter';

import * as Styled from './styled.js';

export default class Filter extends React.Component {
  static propTypes = {
    filter: PropTypes.shape({
      active: PropTypes.bool,
      incidents: PropTypes.bool,
      canceled: PropTypes.bool,
      completed: PropTypes.bool
    }).isRequired,
    onChange: PropTypes.func.isRequired,
    type: PropTypes.oneOf(['running', 'finished']).isRequired
  };
  constructor(props) {
    super(props);
    this.childFilterTypes = Object.keys(props.filter);
  }

  getCheckedChildrenCount = () => {
    return Object.values(this.props.filter).filter(value => value).length;
  };

  isIndeterminate = () => {
    return this.getCheckedChildrenCount() === 1;
  };

  handleChange = type => async () => {
    const {filter} = this.props;
    const newFilter = {
      [type]: filter[type] ? !filter[type] : true
    };

    this.props.onChange(newFilter);
  };

  onResetFilter = () => {
    const change = {};
    const bothChecked = this.getCheckedChildrenCount() === 2;

    this.childFilterTypes.map(type =>
      Object.assign(change, {
        [type]: bothChecked ? !this.props.filter[type] : true
      })
    );

    this.props.onChange(change);
  };

  render() {
    const {type, filter} = this.props;
    return (
      <Styled.Filters>
        <div>
          <Checkbox
            label={LABELS[type]}
            isIndeterminate={this.isIndeterminate()}
            isChecked={this.getCheckedChildrenCount() === 2}
            onChange={this.onResetFilter}
          />
        </div>
        <Styled.NestedFilters>
          {this.childFilterTypes.map(type => {
            return (
              <Checkbox
                key={type}
                label={LABELS[type]}
                isChecked={filter[type] || false}
                onChange={this.handleChange(type)}
              />
            );
          })}
        </Styled.NestedFilters>
      </Styled.Filters>
    );
  }
}
