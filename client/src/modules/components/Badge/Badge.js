import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function Badge(props) {
  return <Styled.Badge {...props} />;
}

Badge.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};
