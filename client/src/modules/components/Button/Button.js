import React from 'react';

import * as Styled from './styled';

export default function Button(props) {
  return (
    <React.Fragment>
      <Styled.Button {...props} />
    </React.Fragment>
  );
}
