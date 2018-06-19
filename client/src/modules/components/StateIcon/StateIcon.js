import React from 'react';
import PropTypes from 'prop-types';

import {themed} from 'modules/theme';

import * as Styled from './styled';

const stateIconsMap = {
  INCIDENT: Styled.IncidentIcon,
  ACTIVE: Styled.ActiveIcon,
  COMPLETED: Styled.CompletedIcon,
  CANCELLED: Styled.CancelledIcon
};

function StateIcon({stateName, toggleTheme, ...props}) {
  const TargetComponent = stateIconsMap[stateName];
  return <TargetComponent {...props} />;
}

export default themed(StateIcon);

StateIcon.propTypes = {
  stateName: PropTypes.oneOf(['INCIDENT', 'ACTIVE', 'COMPLETED', 'CANCELLED']),
  theme: PropTypes.string.isRequired
};
