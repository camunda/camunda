import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function Button(props) {
  return <Styled.Button {...props} />;
}

Button.propTypes = {
  size: PropTypes.oneOf(['medium', 'large']),
  color: PropTypes.oneOf(['main', 'primary'])
};

Button.defaultProps = {
  size: 'medium',
  color: 'main'
};
