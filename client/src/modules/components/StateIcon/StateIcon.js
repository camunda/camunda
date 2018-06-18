import React from 'react';
import PropTypes from 'prop-types';

import {themed} from 'modules/theme';

import * as Styled from './styled';

function StateIcon({stateName, toggleTheme, ...props}) {
  switch (stateName) {
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
  stateName: PropTypes.string.isRequired,
  theme: PropTypes.string.isRequired
};
