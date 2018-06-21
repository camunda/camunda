import React from 'react';

import * as Styled from './styled';

export default function TextInput(props) {
  return (
    <React.Fragment>
      <Styled.Input {...props} />
    </React.Fragment>
  );
}
