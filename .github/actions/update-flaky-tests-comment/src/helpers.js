function parseComment(body) {
  try {
    const match = body.match(/_👻 Haunted Tests — They Fail When No One's Watching_\n\n([\s\S]*?)\n\nIf the changes affect this area/);
    if (!match) return null;

    const section = match[1];
    const regex = /- \*\*(.*?)\*\* – .* \*\*(\d+)% flakiness\*\*\n  - Location: `(.*?)`\n  - Occurrences: (\d+) \/ (\d+)/g;

    const tests = [];
    let totalRuns = 1;
    let m;

    while ((m = regex.exec(section)) !== null) {
      const [_, fullName, , pkg, occurrences, runs] = m;
      const [className, methodName] = fullName.includes('.')
          ? [fullName.substring(0, fullName.lastIndexOf('.')), fullName.split('.').pop()]
          : ['', fullName];

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
      tests: groupByPackage(tests)
    };
  } catch (err) {
    console.error('❌ Failed to parse comment:', err);
    return null;
  }
}

function groupByPackage(tests) {
  const map = {};
  for (const test of tests) {
    if (!map[test.packageName]) map[test.packageName] = [];
    map[test.packageName].push(test);
  }
  return Object.entries(map).map(([packageName, flakys]) => ({ packageName, flakys }));
}

function mergeFlakyData(current, historical) {
  if (!historical) return prepareFirstRunData(current);

  const newTotal = historical.totalRuns + 1;
  const merged = new Map();

  for (const oldTest of historical.tests.flatMap(pkg => pkg.flakys.map(test => ({ ...test, packageName: pkg.packageName })))) {
    const key = getTestKey(oldTest);
    merged.set(key, { ...oldTest, totalRuns: newTotal });
  }

  for (const pkg of current.tests) {
    for (const test of pkg.flakys) {
      const key = `${pkg.packageName}.${test.className}.${test.methodName}`;
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
          packageName: pkg.packageName,
          occurrences: 1,
          totalRuns: newTotal
        });
      }
    }
  }

  const packageMap = {};

  for (const test of merged.values()) {
    if (!packageMap[test.packageName]) packageMap[test.packageName] = [];
    packageMap[test.packageName].push({
      className: test.className,
      methodName: test.methodName,
      jobs: test.jobs,
      occurrences: test.occurrences,
      totalRuns: test.totalRuns
    });
  }

  return {
    tests: Object.entries(packageMap).map(([packageName, flakys]) => ({
      packageName,
      flakys
    }))
  };
}

function prepareFirstRunData(currentData) {
  if (!currentData?.tests?.length) return { tests: [] };

  return {
    tests: currentData.tests.map(pkg => ({
      packageName: pkg.packageName,
      flakys: pkg.flakys.map(test => ({
        ...test,
        occurrences: test.occurrences || 1,
        totalRuns: 1
      }))
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
