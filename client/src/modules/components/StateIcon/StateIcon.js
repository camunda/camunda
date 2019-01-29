import React from 'react';
import PropTypes from 'prop-types';

import {themed} from 'modules/theme';
import {getInstanceState} from 'modules/utils/instance';

import * as Styled from './styled';

const stateIconsMap = {
  INCIDENT: Styled.IncidentIcon,
  ACTIVE: Styled.ActiveIcon,
  COMPLETED: Styled.CompletedIcon,
  CANCELED: Styled.CanceledIcon
};

function StateIcon({instance, ...props}) {
  const computedState = getInstanceState(instance);
  const TargetComponent = stateIconsMap[computedState];
  return <TargetComponent {...props} />;
}

export default themed(StateIcon);

StateIcon.propTypes = {
  instance: PropTypes.shape({
    incidents: PropTypes.arrayOf(PropTypes.object),
    state: PropTypes.string.isRequired.isRequired
  }).isRequired,
  theme: PropTypes.string.isRequired
};
