import {addSplash} from './splash';

const removeSplash = addSplash();

require.ensure(['lodash.isequal', 'redux', 'babel-polyfill', './init', './styles.scss'], () => {
  removeSplash();

  require('./init');
  require('./styles.scss');
});

