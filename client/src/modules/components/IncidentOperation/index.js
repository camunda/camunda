/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {withData} from 'modules/DataManager';

import {OPERATION_TYPE, LOADING_STATE} from 'modules/constants';
import Operations from 'modules/components/Operations';

import OperationItems from 'modules/components/OperationItems';

import * as Styled from './styled';

class IncidentOperation extends React.Component {
  static propTypes = {
    incident: PropTypes.object.isRequired,
    instanceId: PropTypes.string.isRequired,
    showSpinner: PropTypes.bool,
    dataManager: PropTypes.object,
  };

  constructor(props) {
    super(props);

    this.state = {
      isSpinnerVisible: false,
    };
    this.subscriptions = {
      [`OPERATION_APPLIED_INSTANCE_${this.props.instanceId}`]: ({state}) => {
        if (state === LOADING_STATE.LOADING) {
          this.setState({isSpinnerVisible: true});
        }
      },
    };
  }

  componentDidMount() {
    this.props.dataManager.subscribe(this.subscriptions);
  }

  componentWillUnmount() {
    this.props.dataManager.unsubscribe(this.subscriptions);
  }

  handleOnClick = async (e) => {
    e.stopPropagation();
    this.setState({isSpinnerVisible: true});

    const {dataManager, instanceId, incident} = this.props;

    // incidents operations should listen to main btn who publishes the incident ids which are affected
    dataManager.applyOperation(instanceId, {
      operationType: OPERATION_TYPE.RESOLVE_INCIDENT,
      incidentId: incident.id,
    });
  };

  render() {
    const isSpinnerVisible =
      this.state.isSpinnerVisible || this.props.showSpinner;
    return (
      <Styled.Operations>
        {isSpinnerVisible && (
          <Operations.Spinner data-test="operation-spinner" />
        )}

        <OperationItems>
          <OperationItems.Item
            type={OPERATION_TYPE.RESOLVE_INCIDENT}
            onClick={this.handleOnClick}
            data-test="retry-incident"
            title="Retry Incident"
          />
        </OperationItems>
      </Styled.Operations>
    );
  }
}

const WrappedOperation = withData(IncidentOperation);
WrappedOperation.WrappedComponent = IncidentOperation;

export default WrappedOperation;
