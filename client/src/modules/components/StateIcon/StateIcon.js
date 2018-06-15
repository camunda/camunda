import React from 'react';
import PropTypes from 'prop-types';

import {themed} from 'modules/theme';

import * as Styled from './styled';

function StateIcon({instanceState, toggleTheme, ...props}) {
  switch (instanceState) {
    case 'INCIDENT':
      return <Styled.IncidentIcon {...props} />;
    case 'ACTIVE':
      return <Styled.ActiveIcon {...props} />;
    case 'COMPLETED':
      return <Styled.CompletedIcon {...props} />;
    default:
      return <Styled.CanceledIcon {...props} />;
  }
}

export default themed(StateIcon);

StateIcon.propTypes = {
  instanceState: PropTypes.string.isRequired,
  theme: PropTypes.string.isRequired
};
