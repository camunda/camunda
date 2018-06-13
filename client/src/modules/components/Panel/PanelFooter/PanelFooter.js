import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

export default function PanelFooter({children}) {
  return <Styled.Footer>{children}</Styled.Footer>;
}

PanelFooter.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};
