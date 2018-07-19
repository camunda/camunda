import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function Badge(props) {
  return <Styled.Badge {...props} />;
}

Badge.propTypes = {
  type: PropTypes.oneOf([
    'filters',
    'selections',
    'selectionHead',
    'openSelectionHead',
    'incidents',
    'instances'
  ]),
  children: PropTypes.node.isRequired
};
