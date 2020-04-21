/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {LOADING_STATE} from 'modules/constants';
import {withData} from 'modules/DataManager';

import {formatDate} from 'modules/utils/date';
import {getWorkflowName} from 'modules/utils/instance';
import Operations from 'modules/components/Operations';
import Skeleton from './Skeleton';

import * as Styled from './styled';

class InstanceHeader extends React.PureComponent {
  static propTypes = {
    dataManager: PropTypes.object,
    instance: PropTypes.shape({
      id: PropTypes.string.isRequired,
      startDate: PropTypes.string.isRequired,
      endDate: PropTypes.string,
      state: PropTypes.string.isRequired,
      errorMessage: PropTypes.string,
      workflowVersion: PropTypes.number,
      hasActiveOperation: PropTypes.bool,
    }),
  };

  constructor(props) {
    super(props);
    this.state = {
      hasActiveOperation: false,
    };
    this.subscriptions = {};
  }

  componentDidUpdate(prevProps) {
    const {instance, dataManager} = this.props;

    if (
      !prevProps.instance &&
      instance &&
      !dataManager.subscriptions[`OPERATION_APPLIED_INCIDENT_${instance.id}`] &&
      !dataManager.subscriptions[`OPERATION_APPLIED_VARIABLE_${instance.id}`]
    ) {
      this.addSubscriptions();
    }
  }

  componentWillUnmount() {
    this.props.dataManager.unsubscribe(this.subscriptions);
  }

  addSubscriptions() {
    const {instance, dataManager} = this.props;

    this.subscriptions = {
      [`OPERATION_APPLIED_INCIDENT_${instance.id}`]: ({state}) => {
        if (state === LOADING_STATE.LOADING) {
          this.setState({hasActiveOperation: true});
        }
      },
      [`OPERATION_APPLIED_VARIABLE_${instance.id}`]: ({state}) => {
        if (state === LOADING_STATE.LOADING) {
          this.setState({hasActiveOperation: true});
        }
      },
      [`OPERATION_APPLIED_INSTANCE_${instance.id}`]: ({state}) => {
        if (state === LOADING_STATE.LOADING) {
          this.setState({hasActiveOperation: true});
        }
      },
      CONSTANT_REFRESH: ({response, state}) => {
        if (state === LOADING_STATE.LOADED) {
          const {LOAD_VARIABLES, LOAD_INSTANCE} = response;
          if (
            !LOAD_VARIABLES.find((variable) => variable.hasActiveOperation) &&
            !LOAD_INSTANCE.hasActiveOperation
          ) {
            this.setState({hasActiveOperation: false});
          }
        }
      },
    };

    dataManager.subscribe(this.subscriptions);
  }

  render() {
    const {instance} = this.props;
    return (
      <Styled.SplitPaneHeader>
        {!instance ? (
          <Skeleton />
        ) : (
          <Styled.Table>
            <tbody>
              <Styled.Tr>
                <Styled.Td>
                  <Styled.StateIcon state={instance.state} />
                  {getWorkflowName(instance)}
                </Styled.Td>
                <Styled.Td>{instance.id}</Styled.Td>
                <Styled.Td>{`Version ${instance.workflowVersion}`}</Styled.Td>
                <Styled.Td>{formatDate(instance.startDate)}</Styled.Td>
                <Styled.Td>{formatDate(instance.endDate)}</Styled.Td>
                <Styled.Td>
                  <Styled.OperationsWrapper>
                    <Operations
                      instance={instance}
                      forceSpinner={this.state.hasActiveOperation}
                    />
                  </Styled.OperationsWrapper>
                </Styled.Td>
              </Styled.Tr>
            </tbody>
          </Styled.Table>
        )}
      </Styled.SplitPaneHeader>
    );
  }
}

const WrappedInstanceHeader = withData(InstanceHeader);
WrappedInstanceHeader.WrappedComponent = InstanceHeader;

export default WrappedInstanceHeader;
