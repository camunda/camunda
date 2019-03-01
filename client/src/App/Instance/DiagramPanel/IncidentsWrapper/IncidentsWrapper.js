import React from 'react';

import PropTypes from 'prop-types';
import IncidentsBar from './../IncidentsBar';
import IncidentsOverlay from './../IncidentsOverlay';
import IncidentsTable from './../IncidentsTable';

export default class IncidentsWrapper extends React.PureComponent {
  static defaultProps = {
    incidents: PropTypes.array,
    incidentsCount: PropTypes.number,
    instanceId: PropTypes.string
  };
  state = {
    isOpen: true
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
          count={this.props.incidentsCount}
          onClick={this.handleToggle}
          isArrowFlipped={this.state.isOpen}
        />
        {this.state.isOpen && (
          <IncidentsOverlay>
            <IncidentsTable incidents={this.props.incidents} />
          </IncidentsOverlay>
        )}
      </>
    );
  }
}
