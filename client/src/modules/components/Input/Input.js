import React from 'react';

import * as Styled from './styled';

export default function Input(props) {
  const {error} = props;
  return (
    <React.Fragment>
      {error && (
        <Styled.InputError>
          Username and Password do not match
        </Styled.InputError>
      )}
      <Styled.Input {...props} />
    </React.Fragment>
  );
}
