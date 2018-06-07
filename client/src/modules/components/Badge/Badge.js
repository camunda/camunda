import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function Badge({children, type}) {
  return <Styled.Badge type={type}>{children}</Styled.Badge>;
}

Badge.propTypes = {
  type: PropTypes.oneOf(['filters', 'selections', 'incidents', 'instances']),
  children: PropTypes.node.isRequired
};
