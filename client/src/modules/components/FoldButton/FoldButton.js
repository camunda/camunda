import React from 'react';
import PropTypes from 'prop-types';

import {LeftBar, RightBar, UpBar, DownBar} from 'modules/components/Icon';

import * as Styled from './styled.js';

const icon = {
  left: <LeftBar />,
  right: <RightBar />,
  up: <UpBar />,
  down: <DownBar />
};

export default function FoldButton({type}) {
  return <Styled.FoldButton type={type}>{icon[type]}</Styled.FoldButton>;
}

FoldButton.propTypes = {
  type: PropTypes.oneOf(['left', 'right', 'up', 'down'])
};
