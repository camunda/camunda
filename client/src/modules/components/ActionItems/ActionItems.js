import React from 'react';
import PropTypes from 'prop-types';

import {ACTION_TYPE} from 'modules/constants';
import * as Styled from './styled';

const iconsMap = {
  [ACTION_TYPE.RETRY]: Styled.RetryIcon
};

export default function ActionItems(props) {
  return <Styled.Ul {...props} />;
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
        aria-label={props.type.toLowerCase()}
        onClick={props.onClick}
      >
        <Icon />
      </Styled.Button>
    </li>
  );
};

ActionItems.Item.propTypes = {
  type: PropTypes.oneOf(Object.keys(ACTION_TYPE)).isRequired,
  onClick: PropTypes.func.isRequired
};
