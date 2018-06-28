import React from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import Diagram from 'modules/components/Diagram';
import StateIcon from 'modules/components/StateIcon';
import {formatDate} from 'modules/utils/date';

import DiagramBar from './DiagramBar';
import * as Styled from './styled';

const {Pane} = SplitPane;

export default function DiagramPanel({instance, ...props}) {
  return (
    <Pane {...props}>
      <Pane.Header>
        <Styled.Table>
          <tbody>
            <tr>
              <td>
                <StateIcon instance={instance} />
                {instance.workflowId}
              </td>
              <td>{instance.id}</td>
              <td>{formatDate(instance.startDate)}</td>
              <td>{formatDate(instance.endDate)}</td>
            </tr>
          </tbody>
        </Styled.Table>
      </Pane.Header>
      <Pane.Body>
        <DiagramBar instance={instance} />
        <Diagram workflowId={instance.workflowId} />
      </Pane.Body>
    </Pane>
  );
}

DiagramPanel.propTypes = {
  instance: PropTypes.shape({
    id: PropTypes.string.isRequired,
    workflowId: PropTypes.string.isRequired,
    startDate: PropTypes.string.isRequired,
    endDate: PropTypes.string,
    state: PropTypes.string.isRequired,
    errorMessage: PropTypes.string
  }).isRequired,
  paneId: PropTypes.string
};
