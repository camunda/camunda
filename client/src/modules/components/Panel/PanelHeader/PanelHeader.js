import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

export default function PanelHeader(props) {
  return <Styled.Header {...props} />;
}

PanelHeader.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};
