import React from 'react';

import {getInstanceState, getInstanceErrorMessage} from 'modules/utils';

import * as Styled from './styled';

export default function DiagramBar({instance}) {
  if (getInstanceState(instance) === 'INCIDENT') {
    const errorMessage = getInstanceErrorMessage(instance);
    return (
      <Styled.IncidentMessage>
        <strong>Incident:</strong> {errorMessage}
      </Styled.IncidentMessage>
    );
  }

  return null;
}
