// Self-Managed smoke suite (headless Chrome). Extra chrome flags keep the
// sandboxed CI runner stable; quarantine retries flaky/wedged tests.
const suppressBeforeUnload = require('./suppress-beforeunload.cjs');

module.exports = {
  browsers:
    'chrome:headless --disable-features=LocalNetworkAccessChecks --no-sandbox --disable-dev-shm-usage --disable-gpu',
  src: 'e2e/sm-tests/*.js',
  filter: {testMeta: {type: 'smoke'}},
  clientScripts: [{content: suppressBeforeUnload}],
  skipJsErrors: true,
  quarantineMode: {attemptLimit: 3, successThreshold: 1},
  assertionTimeout: 40000,
  testExecutionTimeout: 120000,
  runExecutionTimeout: 720000,
};
