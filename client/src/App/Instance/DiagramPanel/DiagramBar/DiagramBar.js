import React from 'react';
import PropTypes from 'prop-types';

import {getInstanceState, getIncidentMessage} from 'modules/utils/instance';

import {INSTANCE_STATE} from 'modules/constants';

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

DiagramBar.propTypes = {
  instance: PropTypes.object.isRequired
};
