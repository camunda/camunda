require('./bootstrap');

if (isPolyfillNeeded()) {
  require.ensure(['babel-polyfill', 'whatwg-fetch'], () => {
    require('babel-polyfill');
    require('whatwg-fetch');
  });
}

document.body.appendChild(
  document.createTextNode('Hello World! License Page!')
);

require.ensure(['./styles.less'], () => {
  require('./styles.less');
});

function isPolyfillNeeded() {
  return !window.Symbol || !Array.prototype.find;
}
