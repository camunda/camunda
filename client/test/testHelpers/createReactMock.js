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
  Component.oldCalledWith = Comment.calledWith;
  Component.calledWith = (...args) => {
    if (args.length === 1 && typeof args[0] === 'object' && !Array.isArray(args[0])) {
      const predicate = buildPredicateFunction(args[0]);

      return Component.calls.some(([props]) => predicate(props));
    }

    return Component.oldCalledWith(...args);
  };

  return Component;
}

function buildPredicateFunction(predicate) {
  if (typeof predicate === 'object') {
    return attributes => {
      return Object
        .keys(predicate)
        .reduce((result, key) => {
          return result && attributes[key] === predicate[key];
        }, true);
    };
  }

  if (typeof predicate !== 'function') {
    return props => props === predicate;
  }

  return predicate;
}
