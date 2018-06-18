import React, {Component, Fragment} from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';
import StateIcon from 'modules/components/StateIcon';

import Header from '../Header';
import DiagramPanel from './DiagramPanel';

import * as Styled from './styled';
import * as api from './api';

export default class Instance extends Component {
  static propTypes = {
    match: PropTypes.shape({
      params: PropTypes.shape({
        id: PropTypes.string.isRequired
      }).isRequired
    }).isRequired
  };

  state = {
    instance: null,
    loaded: false
  };

  async componentDidMount() {
    const id = this.props.match.params.id;
    const response = await api.workflowInstance(id);
    const instanceStats = this.extractInstanceStats(response);
    this.setState({
      loaded: true,
      instance: {
        id,
        ...instanceStats
      }
    });
  }

  /**
   * extracts only necessary statistics from instance statistics response
   */
  extractInstanceStats = ({
    workflowDefinitionId,
    startDate,
    endDate,
    state,
    incidents
  }) => {
    let instanceStats = {
      workflowDefinitionId,
      startDate,
      endDate,
      stateName: state
    };

    if (state === 'COMPLETED' || state === 'CANCELLED') {
      return instanceStats;
    }

    // get the active incident
    const activeIncident =
      incidents &&
      incidents.length &&
      incidents.filter(({state}) => state === 'ACTIVE')[0];

    if (!activeIncident) {
      return instanceStats;
    }

    instanceStats = {
      ...instanceStats,
      stateName: 'INCIDENT',
      errorMessage: activeIncident.errorMessage
    };

    return instanceStats;
  };

  render() {
    if (!this.state.loaded) {
      return 'Loading';
    }

    const instanceId = this.props.match.params.id;
    const {stateName} = this.state.instance;
    const stateIcon = <StateIcon stateName={stateName} />;

    return (
      <Fragment>
        <Header
          instances={14576}
          filters={9263}
          selections={24}
          incidents={328}
          detail={
            <Fragment>
              {stateIcon} Instance {instanceId}
            </Fragment>
          }
        />
        <Styled.Instance>
          <Styled.Top>
            <DiagramPanel
              instance={this.state.instance}
              stateIcon={stateIcon}
            />
          </Styled.Top>
          <Styled.Bottom>
            <Panel>
              <Panel.Header foldButtonType="right">
                Instance history
              </Panel.Header>
              <Panel.Body />
            </Panel>
          </Styled.Bottom>
        </Styled.Instance>
      </Fragment>
    );
  }
}
