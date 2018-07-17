import React, {Fragment} from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function Button(props) {
  return (
    <Fragment>
      <Styled.Button {...props} />
    </Fragment>
  );
}

Button.propTypes = {
  size: PropTypes.oneOf(['medium', 'large'])
};

Button.defaultProps = {
  size: 'medium'
};
