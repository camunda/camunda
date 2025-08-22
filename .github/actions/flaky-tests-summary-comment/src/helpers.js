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
  methodName = methodName.replace(/\[.*?]\s*$/, '');

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
    const lines = body.split('\n');

    const flakyTests = [];
    let currentTest = {};

    for (const line of lines) {
      //Match method name
      const methodMatch = line.match(/-\s(?:\[\*\*(.+?)\*\*\]\(.+?\)|(\S+?))\s–/);
      if (methodMatch) {
        // If no hyperlink exists
        if (Object.keys(currentTest).length > 0) {
          flakyTests.push(currentTest);
          currentTest = {};
        }
        // methodMatch[1] is for the markdown link, methodMatch[2] for plain text
        currentTest.methodName = methodMatch[1] || methodMatch[2];
      }

      // Match jobs
      const jobsMatch = line.match(/Jobs:\s+`(.+?)`/);
      if (jobsMatch) {
        currentTest.jobs = jobsMatch[1].split(',').map(job => job.trim());
      }

      // Match package
      const pkgMatch = line.match(/Package:\s+`(.+?)`/);
      if (pkgMatch) {
        currentTest.packageName = pkgMatch[1];
      }

      // Match class
      const classMatch = line.match(/Class:\s+`(.+?)`/);
      if (classMatch) {
        currentTest.className = classMatch[1];
      }

      // Match occurrences
      const occMatch = line.match(/Occurrences:\s+(\d+)\s*\/\s*(\d+)/);
      if (occMatch) {
        currentTest.occurrences = parseInt(occMatch[1], 10);
        currentTest.totalRuns = parseInt(occMatch[2], 10);
      }
    }

    // Push last test if any
    if (Object.keys(currentTest).length > 0) {
      flakyTests.push(currentTest);
    }

    return flakyTests;
  } catch (err) {
    console.error('[flaky-tests] Failed to parse comment:', err);
    return null;
  }
}

function mergeFlakyData(current, historical) {

  const newTotal = historical[0].totalRuns + 1;
  const merged = new Map();

  historical.forEach(oldTest => {
    merged.set(getTestKey(oldTest), {...oldTest, totalRuns: newTotal});
  });

  current.forEach(test => {
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
  })

  return Array.from(merged.values(), test => ({
    ...test,
    flakiness: Math.round((test.occurrences / test.totalRuns) * 100)
  }));
}

function prepareFirstRunData(currentData) {
  if (!currentData?.length) return [];

  return currentData.map(test => ({
    ...test,
    occurrences: test.occurrences || 1,
    totalRuns: 1,
    flakiness: (test.occurrences || 1) * 100
  }));
}

function getTestKey(test) {
  const methodName = test.methodName.replace(/\[([^\]]+)]\([^)]+\)/g, '$1');
  return test.fullName
      ? `${test.fullName}`
      : `${test.packageName}.${test.className}.${methodName}`;
}

module.exports = {
  parseTestName,
  getTestKey,
  parseComment,
  prepareFirstRunData,
  mergeFlakyData
};
