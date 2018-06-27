import React, {Children, cloneElement} from 'react';

import {PANE_ID} from './Pane/constants';

import {ExpandProvider} from './ExpandContext';
import Pane from './Pane';
import * as Styled from './styled';

function customArrayProp(props, propName, componentName) {
  const value = props[propName] || {};
  if (
    !Array.isArray(value) ||
    value.length !== 2 ||
    !value.every(React.isValidElement)
  ) {
    return new Error(
      'Invalid prop `' +
        propName +
        '` supplied to' +
        ' `' +
        componentName +
        '`. Validation failed.'
    );
  }

  return null;
}

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
  children: customArrayProp
};

SplitPane.Pane = Pane;
