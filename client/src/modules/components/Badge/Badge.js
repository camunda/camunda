import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function Badge(props) {
  return (
    <Styled.Badge {...props}>
      {props.type === 'comboSelection' && (
        <Styled.Circle>{props.circleContent}</Styled.Circle>
      )}
      {props.badgeContent}
    </Styled.Badge>
  );
}

Badge.propTypes = {
  type: PropTypes.oneOf([
    'filters',
    'selections',
    'selectionHead',
    'openSelectionHead',
    'comboSelection',
    'incidents',
    'instances'
  ]),
  badgeContent: PropTypes.number,
  circleContent: PropTypes.number
};
