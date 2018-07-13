import React from 'react';
import PropTypes from 'prop-types';

import {SORT_ORDER} from 'modules/constants';
import * as Styled from './styled';

export default function SortIcon(props) {
  const TargetIcon =
    props.sortOrder === SORT_ORDER.ASC ? Styled.Up : Styled.Down;
  return (
    <Styled.SortIcon {...props} href="#">
      <TargetIcon sortOrder={props.sortOrder} />
    </Styled.SortIcon>
  );
}

SortIcon.propTypes = {
  sortOrder: PropTypes.oneOf(Object.values(SORT_ORDER)),
  onClick: PropTypes.func
};
