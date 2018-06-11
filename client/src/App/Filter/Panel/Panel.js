import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

export default function Panel({children}) {
  return (
    <Styled.Panel>
      <div>{children}</div>
    </Styled.Panel>
  );
}

Panel.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};
