const helpers = require('./helpers');

function processFlakyTestsData(rawData) {
  const testMap = new Map();

  rawData.forEach(({ job, flaky_tests }) => {
    if (!flaky_tests || flaky_tests.trim() === '') {
      console.log(`[flaky-tests] Skipping job "${job}" - no flaky tests`);
      return;
    }

    const testNames = flaky_tests
        .match(/(?:[^\s(]+(?:\([^)]+\))?(?:\[\d+])?)+/g) || []
        .map(t => t.trim())
        .filter(Boolean);

    testNames.forEach(testName => {
      const parsedTest = helpers.parseTestName(testName);
      if (parsedTest) {
        const key = helpers.getTestKey(parsedTest);
        console.log(`[flaky-tests] Processing test "${key}" from job "${job}"`);
        const existingTest = testMap.get(key);

        if (existingTest) {
          if (!existingTest.jobs.includes(job)) {
            existingTest.jobs.push(job);
          }
          existingTest.occurrences++;
        } else {
          testMap.set(key, {
            ...parsedTest,
            jobs: [job],
            occurrences: 1
          });
        }
      }
    });
  });

  return { flakyTests: Array.from(testMap.values()) };
}

module.exports = { processFlakyTestsData };
