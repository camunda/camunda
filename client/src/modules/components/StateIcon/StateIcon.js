import React from 'react';
import PropTypes from 'prop-types';

import {INSTANCE_STATE} from 'modules/constants';
import {themed} from 'modules/theme';

import * as Styled from './styled';

const stateIconsMap = {
  [INSTANCE_STATE.INCIDENT]: Styled.IncidentIcon,
  [INSTANCE_STATE.ACTIVE]: Styled.ActiveIcon,
  [INSTANCE_STATE.COMPLETED]: Styled.CompletedIcon,
  [INSTANCE_STATE.CANCELED]: Styled.CanceledIcon
};

function StateIcon({state, ...props}) {
  const TargetComponent = stateIconsMap[state];
  return <TargetComponent {...props} />;
}

export default themed(StateIcon);

StateIcon.propTypes = {
  state: PropTypes.oneOf(Object.values(INSTANCE_STATE)).isRequired,
  theme: PropTypes.string.isRequired
};
