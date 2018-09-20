import React from 'react';
import PropTypes from 'prop-types';

import {OPERATION_TYPE} from 'modules/constants';
import * as Styled from './styled';

const iconsMap = {
  [OPERATION_TYPE.UPDATE_RETRIES]: Styled.RetryIcon,
  [OPERATION_TYPE.CANCEL]: Styled.CancelIcon
};

const ariaLabelMap = {
  [OPERATION_TYPE.CANCEL]: 'Cancel',
  [OPERATION_TYPE.UPDATE_RETRIES]: 'Retry'
};

export default function ActionItems(props) {
  return <Styled.Ul {...props}>{props.children}</Styled.Ul>;
}

ActionItems.propTypes = {
  children: PropTypes.node.isRequired
};

ActionItems.Item = function Item(props) {
  const Icon = iconsMap[props.type];

  return (
    <li>
      <Styled.Button
        {...props}
        aria-label={ariaLabelMap[props.type]}
        onClick={props.onClick}
      >
        <Icon />
      </Styled.Button>
    </li>
  );
};

ActionItems.Item.propTypes = {
  type: PropTypes.oneOf(Object.keys(OPERATION_TYPE)).isRequired,
  onClick: PropTypes.func.isRequired
};
