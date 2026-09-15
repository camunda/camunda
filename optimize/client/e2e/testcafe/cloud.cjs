// Cloud smoke test (headless Chrome). Keeps screenshots on failure.
const suppressBeforeUnload = require('./suppress-beforeunload.cjs');

module.exports = {
  browsers: 'chrome:headless --disable-features=LocalNetworkAccessChecks',
  src: 'e2e/cloud-tests/smokeTest.js',
  clientScripts: [{content: suppressBeforeUnload}],
  skipJsErrors: true,
  screenshots: {path: 'build/screenshots', takeOnFails: true},
  assertionTimeout: 40000,
  testExecutionTimeout: 120000,
};
