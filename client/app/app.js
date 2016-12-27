import './styles.scss';

const loader = document.createElement('div');

loader.className = 'start-loader';

document.body.appendChild(loader);

require.ensure(['lodash.isequal', 'redux', 'babel-polyfill', './init'], () => {
  document.body.removeChild(loader);

  require('./init');
});

