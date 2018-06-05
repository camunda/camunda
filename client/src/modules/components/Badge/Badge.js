import React from 'react';

import * as Styled from './styled';

export default function Badge({children, type}) {
  return <Styled.Badge type={type}>{children}</Styled.Badge>;
}
