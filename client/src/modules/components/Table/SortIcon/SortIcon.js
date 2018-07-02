import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function SortIcon(props) {
  const TargetIcon = props.order === 'asc' ? Styled.Up : Styled.Down;
  return (
    <Styled.SortIcon {...props}>
      <TargetIcon order={props.order} />
    </Styled.SortIcon>
  );
}

SortIcon.propTypes = {
  order: PropTypes.oneOf(['asc', 'desc']),
  onClick: PropTypes.func
};
