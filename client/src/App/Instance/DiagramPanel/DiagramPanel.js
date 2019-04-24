/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {formatDate} from 'modules/utils/date';
import {getWorkflowName} from 'modules/utils/instance';
import Actions from 'modules/components/Actions';

import * as Styled from './styled';

export default class DiagramPanel extends React.PureComponent {
  static propTypes = {
    instance: PropTypes.shape({
      id: PropTypes.string.isRequired,
      startDate: PropTypes.string.isRequired,
      endDate: PropTypes.string,
      state: PropTypes.string.isRequired,
      errorMessage: PropTypes.string,
      workflowVersion: PropTypes.number
    }).isRequired,
    children: PropTypes.node,
    forceInstanceSpinner: PropTypes.bool,
    onInstanceOperation: PropTypes.func
  };

  render() {
    const {instance, onInstanceOperation, ...props} = this.props;

    return (
      <Styled.SplitPane {...props}>
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
                      forceSpinner={this.props.forceInstanceSpinner}
                      onButtonClick={onInstanceOperation}
                    />
                  </Styled.ActionsWrapper>
                </Styled.Td>
              </Styled.Tr>
            </tbody>
          </Styled.Table>
        </Styled.SplitPaneHeader>
        <Styled.SplitPaneBody data-test="diagram-panel-body">
          {this.props.children}
        </Styled.SplitPaneBody>
      </Styled.SplitPane>
    );
  }
}
