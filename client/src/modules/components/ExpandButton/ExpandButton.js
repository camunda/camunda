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

export default function ExpandButton({direction, onClick, ...props}) {
  const TargetIcon = iconsMap[direction];

  return (
    <Styled.ExpandButton {...props} onClick={onClick}>
      <TargetIcon />
    </Styled.ExpandButton>
  );
}

ExpandButton.propTypes = {
  direction: PropTypes.oneOf(Object.keys(DIRECTION)),
  onClick: PropTypes.func
};
