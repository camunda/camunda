import {addSplash} from './splash';

const removeSplash = addSplash();

require.ensure(['lodash.isequal', 'redux', './init'], () => {
  removeSplash();

  if (isPolyfillNeeded()) {
    require.ensure(['babel-polyfill'], () => {
      require('babel-polyfill');

      require('./init');
    });
  } else {
    require('./init');
  }
});

require.ensure(['./styles.scss'], () => {
  require('./styles.scss');
});

function isPolyfillNeeded() {
  return !Symbol || !Array.prototype.find;
}
