import React from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import Diagram from 'modules/components/Diagram';
import StateIcon from 'modules/components/StateIcon';
import {formatDate} from 'modules/utils/date';
import {getWorkflowName} from 'modules/utils/instance';
import Actions from 'modules/components/Actions';

import DiagramBar from './DiagramBar';
import * as Styled from './styled';

export default class DiagramPanel extends React.Component {
  static propTypes = {
    instance: PropTypes.shape({
      id: PropTypes.string.isRequired,
      workflowId: PropTypes.string.isRequired,
      startDate: PropTypes.string.isRequired,
      endDate: PropTypes.string,
      state: PropTypes.string.isRequired,
      errorMessage: PropTypes.string,
      workflowVersion: PropTypes.number
    }).isRequired,
    paneId: PropTypes.string,
    onFlowNodesDetailsReady: PropTypes.func,
    selectableFlowNodes: PropTypes.arrayOf(PropTypes.string),
    selectedFlowNode: PropTypes.string,
    onFlowNodeSelected: PropTypes.func,
    flowNodeStateOverlays: PropTypes.array
  };

  render() {
    const {instance, ...props} = this.props;
    return (
      <SplitPane.Pane {...props}>
        <Styled.SplitPaneHeader>
          <Styled.Table>
            <tbody>
              <Styled.Tr>
                <Styled.Td>
                  <StateIcon instance={instance} />
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
        <SplitPane.Pane.Body>
          <DiagramBar instance={instance} />
          <Diagram
            workflowId={instance.workflowId}
            onFlowNodesDetailsReady={this.props.onFlowNodesDetailsReady}
            selectableFlowNodes={this.props.selectableFlowNodes}
            selectedFlowNode={this.props.selectedFlowNode}
            onFlowNodeSelected={this.props.onFlowNodeSelected}
            flowNodeStateOverlays={this.props.flowNodeStateOverlays}
          />
        </SplitPane.Pane.Body>
      </SplitPane.Pane>
    );
  }
}
