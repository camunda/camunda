import React from 'react';

import * as Styled from './styled.js';

export default function StatsTile({name, value, valueColor, themed}) {
  return (
    <div>
      {themed ? (
        <Styled.themedValue>{value}</Styled.themedValue>
      ) : (
        <Styled.Value valueColor={valueColor}>{value}</Styled.Value>
      )}
      <Styled.Name>{name}</Styled.Name>
    </div>
  );
}
