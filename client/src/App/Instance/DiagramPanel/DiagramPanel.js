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
              <td>{stateIcon} Process_definition_name</td>
              <td>{instance.id}</td>
              <td>Flow Node</td>
              <td>{formatDate(instance.startDate)}</td>
              <td>{formatDate(instance.endDate)}</td>
            </tr>
          </tbody>
        </Styled.DiagramPanelHeader>
      </Panel.Header>
      <Panel.Body>
        {instance.instanceState === 'INCIDENT' && (
          <Styled.IncidentMessage>
            <strong>Incident:</strong> {instance.errorMessage}
          </Styled.IncidentMessage>
        )}
        <Diagram />
      </Panel.Body>
    </Panel>
  );
}

DiagramPanel.propTypes = {
  instance: PropTypes.shape({
    id: PropTypes.string.isRequired,
    startDate: PropTypes.string.isRequired,
    endDate: PropTypes.string,
    instanceState: PropTypes.string.isRequired,
    errorMessage: PropTypes.string
  }),
  stateIcon: PropTypes.element.isRequired
};
