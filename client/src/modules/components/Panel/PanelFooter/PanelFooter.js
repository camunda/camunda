import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

export default function PanelFooter(props) {
  const {children} = props;
  return <Styled.Footer {...props}>{children}</Styled.Footer>;
}

PanelFooter.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};
