import React from 'react';

import {
  getInstanceState,
  getIncidentMessage,
  INSTANCE_STATE
} from 'modules/utils';

import * as Styled from './styled';

export default function DiagramBar({instance}) {
  if (getInstanceState(instance) === INSTANCE_STATE.INCIDENT) {
    const errorMessage = getIncidentMessage(instance);
    return (
      <Styled.IncidentMessage>
        <strong>Incident:</strong> {errorMessage}
      </Styled.IncidentMessage>
    );
  }

  return null;
}
