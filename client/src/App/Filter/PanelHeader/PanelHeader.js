import React from 'react';
import PropTypes from 'prop-types';

import {FoldButton} from '../FoldButton';

import * as Styled from './styled.js';

export default function PanelHeader({headline, foldButtonType, children}) {
  return (
    <Styled.Header>
      <Styled.Content>
        <Styled.Headline>{headline}</Styled.Headline>
        <div>{children}</div>
      </Styled.Content>
      {foldButtonType && <FoldButton type={foldButtonType} />}
    </Styled.Header>
  );
}

PanelHeader.propTypes = {
  headline: PropTypes.string,
  foldButtonType: PropTypes.oneOf(['left', 'right']),
  children: PropTypes.element
};
