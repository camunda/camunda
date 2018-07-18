import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function Button(props) {
  return <Styled.Button {...props} />;
}

Button.propTypes = {
  size: PropTypes.oneOf(['medium', 'large'])
};

Button.defaultProps = {
  size: 'medium'
};
