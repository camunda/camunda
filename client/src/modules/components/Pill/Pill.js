import React from 'react';
import PropTypes from 'prop-types';

import {PILL_TYPE} from 'modules/constants';

import * as Styled from './styled';

const iconTypes = {
  [PILL_TYPE.TIMESTAMP]: Styled.Clock
};
export default function Pill(props) {
  const TargetIcon = iconTypes[props.type] || (() => null);
  return (
    <Styled.Pill {...props}>
      <TargetIcon />
      <span>{props.children}</span>
    </Styled.Pill>
  );
}

Pill.propTypes = {
  type: PropTypes.oneOf(Object.values(PILL_TYPE)),
  isActive: PropTypes.bool.isRequired,
  children: PropTypes.string.isRequired
};
