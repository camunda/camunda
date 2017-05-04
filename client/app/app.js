require.ensure(['./bootstrap'], () => {
  require('./bootstrap');
});

if (isPolyfillNeeded()) {
  require.ensure(['babel-polyfill', 'whatwg-fetch'], () => {
    require('babel-polyfill');
    require('whatwg-fetch');

    require('./init');
  });
} else {
  require('./init');
}

require.ensure(['./styles.less'], () => {
  require('./styles.less');
});

function isPolyfillNeeded() {
  return !window.Symbol || !Array.prototype.find;
}
