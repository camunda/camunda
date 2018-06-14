import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

export default function PanelBody(props) {
  return <Styled.Body {...props} />;
}

PanelBody.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};
