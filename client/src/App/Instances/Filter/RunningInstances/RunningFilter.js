import React from 'react';
import PropTypes from 'prop-types';

import Checkbox from 'modules/components/Checkbox';

import * as Styled from './styled.js';

export default class RunningFilter extends React.Component {
  static propTypes = {
    filter: PropTypes.object.isRequired,
    onChange: PropTypes.func.isRequired
  };

  isIndeterminate = () => {
    const {incidents, active} = this.props.filter;

    if (incidents && active) {
      return false;
    }

    // return true if at least one value is true
    return [incidents, active].some(Boolean);
  };

  handleChange = type => async () => {
    const change = {
      [type]: {
        $set: this.props.filter[type] ? !this.props.filter[type] : true
      }
    };

    this.props.onChange(change);
  };

  onResetFilter = () => {
    const change = {
      active: {$set: !this.props.filter.active},
      incidents: {$set: !this.props.filter.incidents}
    };

    this.props.onChange(change);
  };

  render() {
    const {incidents, active} = this.props.filter;

    return (
      <Styled.Filters>
        <div>
          <Checkbox
            label="Running Instances"
            isIndeterminate={this.isIndeterminate()}
            isChecked={active && incidents}
            onChange={this.onResetFilter}
          />
        </div>
        <Styled.NestedFilters>
          <div>
            <Checkbox
              label="Active"
              isChecked={active}
              onChange={this.handleChange('active')}
            />
          </div>
          <div>
            <Checkbox
              label="Incidents"
              isChecked={incidents}
              onChange={this.handleChange('incidents')}
            />
          </div>
        </Styled.NestedFilters>
      </Styled.Filters>
    );
  }
}
