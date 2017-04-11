import isEqual from 'lodash.isequal';

export function createChangeObserver({getter = x => x, equals = isEqual}) {
  let lastValue;

  return {
    observeChanges,
    setLast
  };

  function setLast(value) {
    lastValue = value;
  }

  function observeChanges(listener) {
    return (...args) => {
      const currentValue = getter(...args);

      if (!equals(lastValue, currentValue)) {
        lastValue = currentValue;

        listener(currentValue);
      }
    };
  }
}
