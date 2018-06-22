import React from 'react';
import PropTypes from 'prop-types';

import FoldButton from 'modules/components/FoldButton';

import * as Styled from './styled.js';

export default function PanelHeader(props) {
  const {foldButtonType, children} = props;
  return (
    <Styled.Header {...props}>
      {children}
      {foldButtonType && <FoldButton type={foldButtonType} />}
    </Styled.Header>
  );
}

PanelHeader.propTypes = {
  foldButtonType: PropTypes.string,
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ]),
  isRounded: PropTypes.bool
};

PanelHeader.defaultProps = {
  isRounded: false
};
