import {addSplash} from './splash';

const removeSplash = addSplash();

require.ensure(['lodash.isequal', 'redux', 'babel-polyfill', './init'], () => {
  removeSplash();

  require('./init');
  require('babel-polyfill');
});

require.ensure(['./styles.scss'], () => {
  require('./styles.scss');
});

