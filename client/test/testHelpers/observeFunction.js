import isEqual from 'lodash.isequal';

export function observeFunction(original) {
  const mocks = {};

  function wrapFunction(...args) {
    wrapFunction.calls.push(args);

    return original(...args);
  }

  wrapFunction.calls = [];
  wrapFunction.set = (name, value) => mocks[name] = value;
  wrapFunction.mocks = mocks;
  wrapFunction.calledWith = (...args) => {
    return wrapFunction.calls.some(call => {
      return call
        .slice(0, args.length)
        .every((value, index) => {
          return isEqual(value, args[index]);
        });
    });
  };
  wrapFunction.reset = () => {
    wrapFunction.calls = [];
  };

  Object.defineProperties(wrapFunction, {
    called: {
      get: () => wrapFunction.calls.length >= 1
    },
    calledOnce: {
      get: () => wrapFunction.calls.length === 1
    }
  });

  return wrapFunction;
}
