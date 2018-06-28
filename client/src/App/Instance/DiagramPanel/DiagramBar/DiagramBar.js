import React from 'react';
import PropTypes from 'prop-types';

import {getInstanceState, getIncidentMessage} from 'modules/utils/instance';

import {STATE} from 'modules/constants/instance';

import * as Styled from './styled';

export default function DiagramBar({instance}) {
  if (getInstanceState(instance) === STATE.INCIDENT) {
    const errorMessage = getIncidentMessage(instance);
    return (
      <Styled.IncidentMessage>
        <strong>Incident:</strong> {errorMessage}
      </Styled.IncidentMessage>
    );
  }

  return null;
}

DiagramBar.propTypes = {
  instance: PropTypes.object.isRequired
};
