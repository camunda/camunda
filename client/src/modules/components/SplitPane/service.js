import React from 'react';

export function twoNodesPropType(props, propName, componentName) {
  const value = props[propName] || {};
  if (
    !Array.isArray(value) ||
    value.length !== 2 ||
    !value.every(React.isValidElement)
  ) {
    throw new Error(
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
