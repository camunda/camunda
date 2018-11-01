import React from 'react';
import PropTypes from 'prop-types';

import {OPERATION_TYPE} from 'modules/constants';
import * as Styled from './styled';

const iconsMap = {
  [OPERATION_TYPE.UPDATE_RETRIES]: Styled.RetryIcon,
  [OPERATION_TYPE.CANCEL]: Styled.CancelIcon
};

export default function ActionItems(props) {
  return (
    <Styled.Ul {...props}>{React.Children.toArray(props.children)}</Styled.Ul>
  );
}

ActionItems.propTypes = {
  children: PropTypes.node.isRequired
};

ActionItems.Item = function Item({title, onClick, type, ...rest}) {
  const Icon = iconsMap[type];

  return (
    <li>
      <Styled.Button {...rest} type={type} title={title} onClick={onClick}>
        <Icon />
      </Styled.Button>
    </li>
  );
};

ActionItems.Item.propTypes = {
  type: PropTypes.oneOf(Object.keys(OPERATION_TYPE)).isRequired,
  onClick: PropTypes.func,
  title: PropTypes.string
};
