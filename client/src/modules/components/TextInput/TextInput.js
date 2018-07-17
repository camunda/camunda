import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function TextInput(props) {
  return <Styled.Input {...props} />;
}

TextInput.propTypes = {
  'aria-label': PropTypes.string,
  'aria-required': PropTypes.string,
  name: PropTypes.string,
  onBlur: PropTypes.func,
  onChange: PropTypes.func,
  placeholder: PropTypes.string,
  type: PropTypes.oneOf(['text', 'password']),
  value: PropTypes.string
};
