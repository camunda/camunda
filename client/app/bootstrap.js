import $ from 'jquery';

window.$ = $;
window.jquery = $;
window.jQuery = $;

require.ensure(['bootstrap/dist/js/npm'], () => {
  require('bootstrap/dist/js/npm');
});
