import React from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';
import Diagram from 'modules/components/Diagram';
import {formatDate} from 'modules/utils';

import * as Styled from './styled';

export default function DiagramPanel({instance, stateIcon}) {
  return (
    <Panel>
      <Panel.Header>
        <Styled.DiagramPanelHeader>
          <tbody>
            <tr>
              <td>
                {stateIcon} {instance.workflowDefinitionId}
              </td>
              <td>{instance.id}</td>
              <td>{formatDate(instance.startDate)}</td>
              <td>{formatDate(instance.endDate)}</td>
            </tr>
          </tbody>
        </Styled.DiagramPanelHeader>
      </Panel.Header>
      <Panel.Body>
        {instance.stateName === 'INCIDENT' && (
          <Styled.IncidentMessage>
            <strong>Incident:</strong> {instance.errorMessage}
          </Styled.IncidentMessage>
        )}
        <Diagram workflowDefinitionId={instance.workflowDefinitionId} />
      </Panel.Body>
    </Panel>
  );
}

DiagramPanel.propTypes = {
  instance: PropTypes.shape({
    id: PropTypes.string.isRequired,
    startDate: PropTypes.string.isRequired,
    endDate: PropTypes.string,
    stateName: PropTypes.string.isRequired,
    errorMessage: PropTypes.string
  }),
  stateIcon: PropTypes.element.isRequired
};
