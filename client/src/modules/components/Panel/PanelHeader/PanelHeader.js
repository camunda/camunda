import React from 'react';
import PropTypes from 'prop-types';

import FoldButton from 'modules/components/FoldButton';

import * as Styled from './styled.js';

export default function PanelHeader({headline, foldButtonType, children}) {
  return (
    <Styled.Header>
      {children}
      {foldButtonType && <FoldButton type={foldButtonType} />}
    </Styled.Header>
  );
}

PanelHeader.propTypes = {
  foldButtonType: PropTypes.oneOf(['left', 'right']),
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};
