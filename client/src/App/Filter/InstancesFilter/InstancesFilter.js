import React from 'react';
import PropTypes from 'prop-types';

import {Checkbox} from 'components';

import * as Styled from './styled.js';

export default class InstancesFilter extends React.Component {
  state = {
    active: false,
    incident: false
  };

  handleInputChange = () => {
    if (this.state.active && this.state.incident) {
      this.setState({active: false, incident: false});
    } else {
      this.setState({active: true, incident: true});
    }
  };

  toggleSelected = type => () => {
    this.setState({[type]: !this.state[type]});
  };

  isIndeterminate = () => {
    const {active, incident} = this.state;
    return !!(active ^ incident);
  };

  render() {
    const {active, incident} = this.state;

    return (
      <Styled.Filters>
        <Checkbox
          label="Running Instances"
          indeterminate={this.isIndeterminate()}
          checked={active && incident}
          onChange={this.handleInputChange}
        />
        <Styled.NestedFilters>
          <Checkbox
            label="Active"
            checked={active}
            onChange={this.toggleSelected('active')}
          />
          <Checkbox
            label="Incident"
            checked={incident}
            onChange={this.toggleSelected('incident')}
          />
        </Styled.NestedFilters>
      </Styled.Filters>
    );
  }
}
InstancesFilter.propTypes = {
  type: PropTypes.oneOf(['running'])
};
