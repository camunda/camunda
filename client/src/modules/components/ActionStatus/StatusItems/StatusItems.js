import React from 'react';
import PropTypes from 'prop-types';

import {OPERATION_TYPE} from 'modules/constants';
import * as Styled from './styled';

const iconsMap = {
  [OPERATION_TYPE.UPDATE_RETRIES]: Styled.RetryIcon,
  [OPERATION_TYPE.CANCEL]: Styled.CancelIcon
};

export default function StatusItems(props) {
  return (
    <Styled.Ul {...props}>{React.Children.toArray(props.children)}</Styled.Ul>
  );
}

StatusItems.propTypes = {
  children: PropTypes.node.isRequired
};

StatusItems.Item = function Item(props) {
  const Icon = iconsMap[props.type];

  return (
    <Styled.Li {...props}>
      <Icon />
    </Styled.Li>
  );
};

StatusItems.Item.propTypes = {
  type: PropTypes.oneOf(Object.keys(OPERATION_TYPE)).isRequired,
  onClick: PropTypes.func
};
