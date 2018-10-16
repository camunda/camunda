import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function ComboBadge(props) {
  return <Styled.ComboBadge {...props} />;
}

ComboBadge.Left = function Left(props) {
  return <Styled.Left {...props} />;
};

ComboBadge.Right = function Right(props) {
  return <Styled.Right {...props} />;
};

ComboBadge.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};
