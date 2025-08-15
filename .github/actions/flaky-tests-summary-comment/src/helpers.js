function parseTestName(testName) {
  const lastDotIndex = testName.lastIndexOf('.');

  if (lastDotIndex === -1) {
    console.warn(`[flaky-tests] Could not parse test name: ${testName}`);
    return {
      fullName: testName.trim()
    };
  }

  const fullyQualifiedClass = testName.slice(0, lastDotIndex);
  let methodName = testName.slice(lastDotIndex + 1);

  // Remove trailing [index] if it exists
  methodName = methodName.replace(/\[\d+]\s*$/, '');

  // Remove parameter list if it exists
  methodName = methodName.replace(/\(.*?\)\s*$/, '').trim();

  const classParts = fullyQualifiedClass.split('.');
  const className = classParts[classParts.length - 1];
  const packageName = classParts.slice(0, -1).join('.');

  return {
    packageName,
    className,
    methodName
  };
}

function parseComment(body) {
  try {
    const match = body.match(/_👻 Haunted Tests.*?\n\n([\s\S]*?)\n\nIf the changes affect/i);
    if (!match) return null;

    const section = match[1];
    const regex = /- \[\*\*(.*?)\*\*\](?:\(.*?\))? – .*?(\d+)% flakiness\*\*\n\s*- Package: `(.*?)`\n\s*- Class: `(.*?)`\n\s*- Occurrences: (\d+) \/ (\d+)/g;

    const flakyTests = [];
    let m;

    while ((m = regex.exec(section)) !== null) {
      const [_, methodName, , packageName, className, occurrences] = m;

      flakyTests.push({
        packageName,
        className,
        methodName,
        jobs: [],
        occurrences: +occurrences
      });
    }

    return {
      flakyTests
    };
  } catch (err) {
    console.error('[flaky-tests] Failed to parse comment:', err);
    return null;
  }
}

function mergeFlakyData(current, historical) {
  if (!historical) return prepareFirstRunData(current);

  const newTotal = historical.totalRuns + 1;
  const merged = new Map();

  for (const oldTest of historical.flakyTests) {
    const key = getTestKey(oldTest);
    merged.set(key, { ...oldTest, totalRuns: newTotal });
  }

  for (const test of current.flakyTests) {
    const key = getTestKey(test);
    const existing = merged.get(key);

    if (existing) {
      const jobs = [...new Set([...existing.jobs, ...test.jobs])];
      merged.set(key, {
        ...existing,
        jobs,
        occurrences: existing.occurrences + 1,
        totalRuns: newTotal
      });
    } else {
      merged.set(key, {
        ...test,
        occurrences: 1,
        totalRuns: newTotal
      });
    }
  }

  const flakys = [];
  for (const test of merged.values()) {
    const flakiness = Math.round((test.occurrences / test.totalRuns) * 100);
    flakys.push({ ...test, flakiness });
  }

  return flakys;
}

function prepareFirstRunData(currentData) {
  if (!currentData?.flakyTests?.length) return { flakyTests: [] };

  return {
    flakyTests: currentData.flakyTests.map(test => ({
      ...test,
      occurrences: test.occurrences || 1,
      totalRuns: 1,
      flakiness: (test.occurrences || 1) * 100
    }))
  };
}

function getTestKey(test) {
  return test.fullName
    ? `${test.fullName}`
    : `${test.packageName}.${test.className}.${test.methodName}`;
}

module.exports = {
  parseTestName,
  getTestKey,
  parseComment,
  mergeFlakyData
};
