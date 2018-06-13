import React from 'react';

import Panel from 'modules/components/Panel';
import Diagram from 'modules/components/Diagram';
import {Colors} from 'modules/theme';

import * as Styled from './styled';

export default function DiagramPanel({instanceId}) {
  return (
    <Panel>
      <Panel.Header>
        <Styled.DiagramPanelHeader>
          <tbody>
            <tr>
              <td>
                <Styled.IncidentIcon color={Colors.incidentsAndErrors} />{' '}
                Process_definition_name
              </td>
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
