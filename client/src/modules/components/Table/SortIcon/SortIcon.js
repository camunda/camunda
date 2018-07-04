import React from 'react';
import PropTypes from 'prop-types';

import {ORDER} from './constants';
import * as Styled from './styled';

export default function SortIcon(props) {
  const TargetIcon = props.order === ORDER.ASC ? Styled.Up : Styled.Down;
  return (
    <Styled.SortIcon {...props}>
      <TargetIcon order={props.order} />
    </Styled.SortIcon>
  );
}

SortIcon.propTypes = {
  order: PropTypes.oneOf(Object.values(ORDER)),
  onClick: PropTypes.func
};
