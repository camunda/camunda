import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

const Button = React.forwardRef(function Button(props, ref) {
  return <Styled.Button {...props} ref={ref} />;
});

Button.propTypes = {
  size: PropTypes.oneOf(['small', 'medium', 'large']),
  color: PropTypes.oneOf(['main', 'primary'])
};

Button.defaultProps = {
  size: 'medium',
  color: 'main'
};

export default Button;
