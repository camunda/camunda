import React from 'react';

import * as Styled from './styled.js';

export default function StatsTile({name, value, valueColor}) {
  return (
    <div>
      {valueColor === 'themed' ? (
        <Styled.themedValue>{value}</Styled.themedValue>
      ) : (
        <Styled.Value valueColor={valueColor}>{value}</Styled.Value>
      )}
      <Styled.Name>{name}</Styled.Name>
    </div>
  );
}
