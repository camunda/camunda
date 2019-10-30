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
import Actions from 'modules/components/Actions';

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
      hasActiveOperation: PropTypes.bool
    }).isRequired
  };

  constructor(props) {
    super(props);
    this.state = {
      hasActiveOperation: false
    };
    this.subscriptions = {
      [`OPERATION_APPLIED_INCIDENT_${props.instance.id}`]: ({state}) => {
        if (state === LOADING_STATE.LOADING) {
          this.setState({hasActiveOperation: true});
        }
      }
    };
  }

  componentDidMount() {
    this.props.dataManager.subscribe(this.subscriptions);
  }

  componentDidUpdate(prevProps) {
    const {hasActiveOperation: prevHasActiveOperation} = prevProps.instance;
    const {hasActiveOperation} = this.props.instance;

    if (hasActiveOperation !== prevHasActiveOperation) {
      this.setState({hasActiveOperation});
    }
  }

  componentWillUnmount() {
    this.props.dataManager.unsubscribe(this.subscriptions);
  }

  render() {
    const {instance} = this.props;

    return (
      <Styled.SplitPaneHeader>
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
                <Styled.ActionsWrapper>
                  <Actions
                    instance={instance}
                    forceSpinner={this.state.hasActiveOperation}
                  />
                </Styled.ActionsWrapper>
              </Styled.Td>
            </Styled.Tr>
          </tbody>
        </Styled.Table>
      </Styled.SplitPaneHeader>
    );
  }
}

const WrappedInstanceHeader = withData(InstanceHeader);
WrappedInstanceHeader.WrappedComponent = InstanceHeader;

export default WrappedInstanceHeader;
