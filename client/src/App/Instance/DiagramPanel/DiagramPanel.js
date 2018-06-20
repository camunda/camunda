import React from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';
import Diagram from 'modules/components/Diagram';
import StateIcon from 'modules/components/StateIcon';
import {formatDate} from 'modules/utils';

import DiagramBar from './DiagramBar';
import * as Styled from './styled';

export default function DiagramPanel({instance}) {
  return (
    <Panel>
      <Panel.Header>
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
      </Panel.Header>
      <Panel.Body>
        <DiagramBar instance={instance} />
        <Diagram workflowId={instance.workflowId} />
      </Panel.Body>
    </Panel>
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
  }).isRequired
};
