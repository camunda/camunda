import React from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import StateIcon from 'modules/components/StateIcon';
import {formatDate} from 'modules/utils/date';
import {getWorkflowName} from 'modules/utils/instance';
import Actions from 'modules/components/Actions';

import {STATE} from 'modules/constants';

import IncidentsWrapper from './IncidentsWrapper';
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
    children: PropTypes.node
  };

  render() {
    const {instance, ...props} = this.props;
    const hasActiveIncidents = this.props.instance.state === STATE.INCIDENT;

    return (
      <SplitPane.Pane {...props}>
        <Styled.SplitPaneHeader>
          <Styled.Table>
            <tbody>
              <Styled.Tr>
                <Styled.Td>
                  <StateIcon state={instance.state} />
                  {getWorkflowName(instance)}
                </Styled.Td>
                <Styled.Td>{instance.id}</Styled.Td>
                <Styled.Td>{`Version ${instance.workflowVersion}`}</Styled.Td>
                <Styled.Td>{formatDate(instance.startDate)}</Styled.Td>
                <Styled.Td>{formatDate(instance.endDate)}</Styled.Td>
                <Styled.Td>
                  <Styled.ActionsWrapper>
                    <Actions instance={instance} />
                  </Styled.ActionsWrapper>
                </Styled.Td>
              </Styled.Tr>
            </tbody>
          </Styled.Table>
        </Styled.SplitPaneHeader>
        <Styled.SplitPaneBody data-test="diagram-panel-body">
          {hasActiveIncidents && (
            <IncidentsWrapper
              incidents={this.props.incidents}
              instanceId={instance.id}
            />
          )}
          {this.props.children}
        </Styled.SplitPaneBody>
      </SplitPane.Pane>
    );
  }
}
