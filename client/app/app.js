import './bootstrap';
import runtime from 'serviceworker-webpack-plugin/lib/runtime';

if ('serviceWorker' in navigator && process.env.NODE_ENV === 'production') {
  runtime.register();
}

if (isPolyfillNeeded()) {
  require.ensure(['babel-polyfill', 'whatwg-fetch'], () => {
    require('babel-polyfill');
    require('whatwg-fetch');

    initialize();
  });
} else {
  initialize();
}

require.ensure(['./styles.less'], () => {
  require('./styles.less');
});

function isPolyfillNeeded() {
  return !window.Symbol || !Array.prototype.find;
}

function initialize() {
  const {Main, reducer} = require('main');
  const {init} = require('./init');

  init(Main, reducer);
}
