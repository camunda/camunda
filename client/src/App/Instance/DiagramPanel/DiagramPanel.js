import React from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';
import Diagram from 'modules/components/Diagram';

import * as Styled from './styled';

export default function DiagramPanel({instanceId, stateIcon}) {
  return (
    <Panel>
      <Panel.Header>
        <Styled.DiagramPanelHeader>
          <tbody>
            <tr>
              <td>{stateIcon} Process_definition_name</td>
              <td>{instanceId}</td>
              <td>Flow Node</td>
              <td>12 Dec 2018 | 00:00:00</td>
              <td>--</td>
            </tr>
          </tbody>
        </Styled.DiagramPanelHeader>
      </Panel.Header>
      <Panel.Body>
        <Styled.IncidentMessage>
          <strong>Incident:</strong> Error Message goes here
        </Styled.IncidentMessage>
        <Diagram />
      </Panel.Body>
    </Panel>
  );
}

DiagramPanel.propTypes = {
  instanceId: PropTypes.string.isRequired,
  stateIcon: PropTypes.element.isRequired
};
