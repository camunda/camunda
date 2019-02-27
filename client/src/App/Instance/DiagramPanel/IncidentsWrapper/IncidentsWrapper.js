import React from 'react';

import PropTypes from 'prop-types';
import IncidentsBar from './../IncidentsBar';
import Incidents from './../Incidents';

export default class IncidentsWrapper extends React.PureComponent {
  static defaultProps = {
    incidents: PropTypes.object,
    instanceId: PropTypes.string
  };
  state = {
    isOpen: false
  };

  handleToggle = () => {
    this.setState(prevState => ({
      isOpen: !prevState.isOpen
    }));
  };

  render() {
    return (
      <>
        <IncidentsBar
          id={this.props.instanceId}
          count={this.props.incidents.count}
          onClick={this.handleToggle}
          isArrowFlipped={this.state.isOpen}
        />
        {this.state.isOpen && (
          <Incidents>
            <div>Incidents table</div>
          </Incidents>
        )}
      </>
    );
  }
}
