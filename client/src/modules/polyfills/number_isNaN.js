// source: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/isNaN#Polyfill
Number.isNaN =
  Number.isNaN ||
  function(value) {
    // eslint-disable-next-line no-self-compare
    return value !== value;
  };
