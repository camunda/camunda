import React from 'react';
import {observeFunction} from './observeFunction';

export function createReactMock(text, applyChildren, element = 'div') {
  const Component = observeFunction(({children}) => {
    return React.createElement(
      element,
      {
        className: text
      },
      text,
      applyChildren ? children : null
    );
  });

  Component.text = text;
  Component.oldCalledWith = Component.calledWith;
  Component.calledWith = (...args) => {
    if (args.length === 1 && typeof args[0] === 'object' && !Array.isArray(args[0])) {
      const predicate = buildPredicateFunction(args[0]);

      return Component.calls.some(([props]) => predicate(props));
    }

    return Component.oldCalledWith(...args);
  };
  Component.getProperty = withCall(() => Component.calls, (name, [props]) => props[name]);

  return Component;
}

function withCall(getCalls, clientFn) {
  const conf = {
    arity: clientFn.length,
    getCalls
  };

  return withIndex(conf, (...args) => {
    const calls = getCalls();
    const clientArgs = args.length === conf.arity ? args.slice(0, args.length - 1) : args;
    const index = args.length === conf.arity ? args[args.length - 1] : 0;
    const call = calls[index];

    if (calls.length <= index) {
      throw `Could not find call with index ${index}, calls length is ${calls.length}`;
    }

    return clientFn(...clientArgs, call);
  });
}

function withIndex({arity, getCalls, throwOnFail = true}, method) {
  return (...args) => {
    const lastArg = args[args.length - 1];

    if (args.length < arity || typeof lastArg === 'number') {
      return method(...args);
    }

    const calls = getCalls();
    const index = findIndex(lastArg, calls, throwOnFail);

    return method(...args.slice(0, args.length - 1), index);
  };
}

function findIndex(predicate, calls, throwOnFail) {
  const predicateFn = buildPredicateFunction(predicate);

  for (let i = 0; i < calls.length; i++) {
    const attributes = calls[i][0];

    if (predicateFn(attributes)) {
      return i;
    }
  }

  if (throwOnFail) {
    throw new Error('Could not find index matching given predicate');
  }
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
