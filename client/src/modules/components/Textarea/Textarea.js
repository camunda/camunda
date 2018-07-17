import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function Textarea(props) {
  return <Styled.Textarea {...props} />;
}

Textarea.propTypes = {
  'aria-label': PropTypes.string,
  name: PropTypes.string,
  onBlur: PropTypes.func,
  onChange: PropTypes.func,
  placeholder: PropTypes.string,
  value: PropTypes.string
};
