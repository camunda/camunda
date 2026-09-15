// Nightly full Self-Managed suite (headless Chrome, 3-way concurrency).
const suppressBeforeUnload = require('./suppress-beforeunload.cjs');

module.exports = {
  browsers: 'chrome:headless --disable-features=LocalNetworkAccessChecks',
  src: 'e2e/sm-tests/*.js',
  concurrency: 3,
  clientScripts: [{content: suppressBeforeUnload}],
  skipJsErrors: true,
  disableScreenshots: true,
  quarantineMode: {attemptLimit: 3, successThreshold: 1},
  assertionTimeout: 40000,
  testExecutionTimeout: 120000,
};
