import {addSplash} from './splash';

const removeSplash = addSplash();

require.ensure(['lodash.isequal', 'redux', './init'], () => {
  if (isPolyfillNeeded()) {
    require.ensure(['babel-polyfill'], () => {
      removeSplash();

      require('babel-polyfill');

      require('./init');
    });
  } else {
    removeSplash();

    require('./init');
  }
});

require.ensure(['./styles.scss'], () => {
  require('./styles.scss');
});

function isPolyfillNeeded() {
  return !Symbol || !Array.prototype.find;
}
