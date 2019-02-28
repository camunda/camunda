import React from 'react';

import PropTypes from 'prop-types';
import IncidentsBar from './../IncidentsBar';
import IncidentsOverlay from './../IncidentsOverlay';

export default class IncidentsWrapper extends React.PureComponent {
  static defaultProps = {
    incidents: PropTypes.shape({
      count: PropTypes.number,
      incidents: PropTypes.array
    }),
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
          <IncidentsOverlay>
            <div>Incidents table</div>
          </IncidentsOverlay>
        )}
      </>
    );
  }
}
