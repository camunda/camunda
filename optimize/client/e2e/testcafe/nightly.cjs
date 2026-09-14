// Nightly full Self-Managed suite (headless Chrome, 3-way concurrency).
module.exports = {
  browsers: 'chrome:headless --disable-features=LocalNetworkAccessChecks',
  src: 'e2e/sm-tests/*.js',
  concurrency: 3,
  skipJsErrors: true,
  disableScreenshots: true,
  quarantineMode: {attemptLimit: 3, successThreshold: 1},
  assertionTimeout: 40000,
  testExecutionTimeout: 120000,
};
