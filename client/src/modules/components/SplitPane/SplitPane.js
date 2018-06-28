import React, {Children, cloneElement} from 'react';

import {PANE_ID} from './Pane/constants';

import {ExpandProvider} from './ExpandContext';
import Pane from './Pane';
import {twoNodesPropType} from './service';
import * as Styled from './styled';

const paneIds = [PANE_ID.TOP, PANE_ID.BOTTOM];

export default function SplitPane(props) {
  const children = Children.map(props.children, (child, idx) =>
    cloneElement(child, {paneId: paneIds[idx]})
  );

  return (
    <ExpandProvider>
      <Styled.SplitPane {...props}>{children}</Styled.SplitPane>
    </ExpandProvider>
  );
}

SplitPane.propTypes = {
  children: twoNodesPropType
};

SplitPane.Pane = Pane;
