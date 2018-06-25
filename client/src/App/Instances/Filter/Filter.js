import React from 'react';
import PropTypes from 'prop-types';

import Checkbox from 'modules/components/Checkbox';

import * as Styled from './styled.js';

export default class Filter extends React.Component {
  static proptyes = {
    filter: PropTypes.object.isRequired,
    onChange: PropTypes.func.isRequired,
    onResetFilter: PropTypes.func.isRequired
  };

  isIndeterminate = () => {
    const {incidents, active} = this.props.filter;

    if (incidents && active) {
      return false;
    }

    // return true if at least one value is true
    return [incidents, active].some(Boolean);
  };

  render() {
    const {incidents, active} = this.props.filter;
    console.log(this.isIndeterminate());
    return (
      <Styled.Filters>
        <div>
          <Checkbox
            label="Running Instances"
            isIndeterminate={this.isIndeterminate()}
            isChecked={active && incidents}
            onChange={this.props.onResetFilter}
          />
        </div>
        <Styled.NestedFilters>
          <div>
            <Checkbox
              label="Active"
              isChecked={active}
              onChange={this.props.onChange('active')}
            />
          </div>
          <div>
            <Checkbox
              label="Incident"
              isChecked={incidents}
              onChange={this.props.onChange('incidents')}
            />
          </div>
        </Styled.NestedFilters>
      </Styled.Filters>
    );
  }
}
