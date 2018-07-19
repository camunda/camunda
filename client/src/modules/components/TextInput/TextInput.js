import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function TextInput(props) {
  return <Styled.Input {...props} aria-label={props.placeholder} />;
}

TextInput.propTypes = {
  placeholder: PropTypes.string
};
