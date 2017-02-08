import {addSplash} from './splash';

const removeSplash = addSplash();

require.ensure(['lodash.isequal', 'redux', './init'], () => {
  if (isPolyfillNeeded()) {
    require.ensure(['babel-polyfill', 'whatwg-fetch'], () => {
      removeSplash();

      require('babel-polyfill');
      require('whatwg-fetch');

      require('./init');
    });
  } else {
    removeSplash();

    require('./init');
  }
});

require.ensure(['./styles.less'], () => {
  require('./styles.less');
});

require.ensure(['./bootstrap'], () => {
  require('./bootstrap');
});

function isPolyfillNeeded() {
  return !window.Symbol || !Array.prototype.find;
}
