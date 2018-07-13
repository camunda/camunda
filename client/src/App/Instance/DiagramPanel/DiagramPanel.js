import React from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import Diagram from 'modules/components/Diagram';
import StateIcon from 'modules/components/StateIcon';
import {formatDate} from 'modules/utils/date';
import {getWorkflowName} from 'modules/utils/instance';

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
      errorMessage: PropTypes.string
    }).isRequired,
    paneId: PropTypes.string,
    onActivitiesInfoReady: PropTypes.func
  };

  render() {
    const {instance, ...props} = this.props;
    return (
      <SplitPane.Pane {...props}>
        <SplitPane.Pane.Header>
          <Styled.Table>
            <tbody>
              <tr>
                <td>
                  <StateIcon instance={instance} />
                  {getWorkflowName(instance)}
                </td>
                <td>{instance.id}</td>
                <td>{formatDate(instance.startDate)}</td>
                <td>{formatDate(instance.endDate)}</td>
              </tr>
            </tbody>
          </Styled.Table>
        </SplitPane.Pane.Header>
        <SplitPane.Pane.Body>
          <DiagramBar instance={instance} />
          <Diagram
            workflowId={instance.workflowId}
            onActivitiesInfoReady={this.props.onActivitiesInfoReady}
          />
        </SplitPane.Pane.Body>
      </SplitPane.Pane>
    );
  }
}
