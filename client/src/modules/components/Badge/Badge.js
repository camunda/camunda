import React from 'react';
import PropTypes from 'prop-types';

import {BADGE_TYPE} from 'modules/constants';

import * as Styled from './styled';

export default function Badge(props) {
  const isRoundBagde =
    props.children.toString().length === 1 && props.position === 0;
  const Component = isRoundBagde ? Styled.BadgeCircle : Styled.Badge;

  return <Component {...props} />;
}

Badge.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ]),
  type: PropTypes.oneOf(Object.keys(BADGE_TYPE)),
  isActive: PropTypes.bool
};

Badge.defaultProps = {
  isActive: true,
  /* position of Badge in ComboBadge; independent Badges have position 0 */
  position: 0
};
