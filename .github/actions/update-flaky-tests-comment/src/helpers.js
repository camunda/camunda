function parseComment(body) {
  try {
    const match = body.match(/_👻 Haunted Tests — They Fail When No One's Watching_\n\n([\s\S]*?)\n\nIf the changes affect this area/);
    if (!match) return null;

    const section = match[1];
    const regex = /- \*\*(.*?)\*\* – .* \*\*(\d+)% flakiness\*\*\n {2}- Location: `(.*?)`\n {2}- Occurrences: (\d+) \/ (\d+)/g;

    const tests = [];
    let totalRuns = 1;
    let m;

    while ((m = regex.exec(section)) !== null) {
      const [_, fullName, , pkg, occurrences, runs] = m;
      const lastDotIndex = fullName.lastIndexOf('.');
      const className = lastDotIndex > -1 ? fullName.substring(0, lastDotIndex) : '';
      const methodName = lastDotIndex > -1 ? fullName.substring(lastDotIndex + 1) : fullName;

      tests.push({
        packageName: pkg,
        className,
        methodName,
        jobs: [],
        occurrences: +occurrences,
        totalRuns: +runs
      });

      totalRuns = +runs;
    }

    return {
      totalRuns,
      flakys: tests
    };
  } catch (err) {
    console.error('❌ Failed to parse comment:', err);
    return null;
  }
}

function mergeFlakyData(current, historical) {
  if (!historical) return prepareFirstRunData(current);

  const newTotal = historical.totalRuns + 1;
  const merged = new Map();

  for (const oldTest of historical.flakys) {
    const key = getTestKey(oldTest);
    merged.set(key, { ...oldTest, totalRuns: newTotal });
  }

  for (const test of current.flakys) {
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

  return { flakys };
}

function prepareFirstRunData(currentData) {
  if (!currentData?.flakys?.length) return { flakys: [] };

  return {
    flakys: currentData.flakys.map(test => ({
      ...test,
      occurrences: test.occurrences || 1,
      totalRuns: 1,
      flakiness: (test.occurrences || 1) * 100
    }))
  };
}

function getTestKey(test) {
  return `${test.packageName}.${test.className}.${test.methodName}`;
}

module.exports = {
  parseComment,
  mergeFlakyData
};
