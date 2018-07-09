import React from 'react';
import PropTypes from 'prop-types';

import {DIRECTION} from 'modules/constants';
import * as Styled from './styled';

const iconsMap = {
  [DIRECTION.UP]: Styled.Up,
  [DIRECTION.DOWN]: Styled.Down,
  [DIRECTION.LEFT]: Styled.Left,
  [DIRECTION.RIGHT]: Styled.Right
};

export default function ExpandButton({iconDirection, onClick, ...props}) {
  const TargetIcon = iconsMap[iconDirection];

  return (
    <Styled.ExpandButton {...props} onClick={onClick}>
      <TargetIcon />
    </Styled.ExpandButton>
  );
}

ExpandButton.propTypes = {
  iconDirection: PropTypes.oneOf(Object.keys(DIRECTION)),
  onClick: PropTypes.func
};
