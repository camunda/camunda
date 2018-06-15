import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function StateIcon({instanceState, ...props}) {
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

StateIcon.propTypes = {
  instanceState: PropTypes.string.isRequired
};
