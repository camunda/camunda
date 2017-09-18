import React from 'react';
import {observeFunction} from './observeFunction';

const jsx = React.createElement;

export function createReactMock(text, applyChildren) {
  const Component = observeFunction(({children}) => {
    return <div>
      {text}
      {applyChildren ? children : null}
    </div>;
  });

  Component.text = text;

  return Component;
}
