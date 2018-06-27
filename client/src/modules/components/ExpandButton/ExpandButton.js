import React from 'react';
import PropTypes from 'prop-types';

import {ICON_DIRECTION} from './constants';
import * as Styled from './styled';

const iconsMap = {
  [ICON_DIRECTION.UP]: Styled.Up,
  [ICON_DIRECTION.DOWN]: Styled.Down,
  [ICON_DIRECTION.LEFT]: Styled.Left,
  [ICON_DIRECTION.RIGHT]: Styled.Right
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
  iconDirection: PropTypes.oneOf(Object.keys(ICON_DIRECTION)),
  onClick: PropTypes.func
};
