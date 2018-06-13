import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

export default function FoldButton({type}) {
  return <Styled.FoldButton type={type}>x</Styled.FoldButton>;
}

FoldButton.propTypes = {
  type: PropTypes.oneOf(['left', 'right'])
};
