import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function Textarea(props) {
  return <Styled.Textarea aria-label={props.placeholder} {...props} />;
}

Textarea.propTypes = {
  placeholder: PropTypes.string
};
