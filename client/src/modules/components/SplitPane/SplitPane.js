import React, {Children, cloneElement} from 'react';

import {EXPAND_CONTAINER} from 'modules/utils';

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

const containerIds = [EXPAND_CONTAINER.TOP, EXPAND_CONTAINER.BOTTOM];

export default function SplitPane(props) {
  const children = Children.map(props.children, (child, idx) =>
    cloneElement(child, {containerId: containerIds[idx]})
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
