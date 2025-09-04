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
      const methodMatch = line.match(/-\s(?:\[\*\*(.+?)\*\*\]\(.+?\))/);
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

      // Match overall retries
      const overallMatch = line.match(/Overall retries:\s+(?:\*\*)?(\d+)(?:\*\*)?\s+\(per run:\s+\[([^\]]+)\]\)/);
      if (overallMatch) {
        currentTest.overallRetries = parseInt(overallMatch[1], 10);
        currentTest.failuresHistory = overallMatch[2].split(',').map(n => parseInt(n.trim(), 10));
      }

      // Match pipeline runs
      const runsMatch = line.match(/Pipeline runs:\s+(\d+)/);
      if (runsMatch) {
        currentTest.totalRuns = parseInt(runsMatch[1], 10);
      }

      // Match occurrences (older format - for backward compatibility)
      // todo: remove ASAP
      const occMatch = line.match(/Occurrences:\s+(\d+)\s*\/\s*(\d+)/);
      if (occMatch) {
        // This is old format, transform to new structure
        const occurrences = parseInt(occMatch[1], 10);
        const totalRuns = parseInt(occMatch[2], 10);
        
        // For old format, we assume failures were distributed across early runs
        // Create a history array where failures are spread at the beginning
        const history = Array(totalRuns).fill(0);
        for (let i = 0; i < Math.min(occurrences, totalRuns); i++) {
          history[i] = 1;
        }
        
        currentTest.failuresHistory = history;
        currentTest.overallRetries = occurrences;
        currentTest.totalRuns = totalRuns;
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

  // First, add all historical tests and prepare them for the new run
  // If they don't appear in current run, they'll have 0 failures
  historical.forEach(oldTest => {
    if (oldTest.failuresHistory && Array.isArray(oldTest.failuresHistory)) {
      merged.set(getTestKey(oldTest), {
        ...oldTest,
        failuresHistory: [...oldTest.failuresHistory, 0],
        totalRuns: newTotal
      });
    }
  });

  current.forEach(test => {
    const key = getTestKey(test);
    const existing = merged.get(key);

    if (existing) {
      const jobs = [...new Set([...existing.jobs, ...test.jobs])];
      existing.failuresHistory[existing.failuresHistory.length - 1] = test.currentRunFailures || 0;
      existing.overallRetries += test.currentRunFailures || 0;

      merged.set(key, {
        ...existing,
        jobs
      });
    } else {
      // Test appears for the first time in the current run
      // Pad with zeros for all previous runs where this test didn't fail
      const previousRuns = newTotal - 1;
      const paddedHistory = Array(previousRuns).fill(0).concat(test.currentRunFailures || 0);
      
      merged.set(key, {
        ...test,
        failuresHistory: paddedHistory,
        overallRetries: test.currentRunFailures || 0,
        totalRuns: newTotal
      });
    }
  });

  return Array.from(merged.values());
}

function prepareFirstRunData(currentData) {
  if (!currentData?.length) return [];

  return currentData.map(test => {
    const currentRunFailures = test.currentRunFailures || 1;
    return {
      ...test,
      failuresHistory: [currentRunFailures],
      overallRetries: currentRunFailures,
      totalRuns: 1
    };
  });
}

function getTestKey(test) {
  if (test.fullName) {
    return `${test.fullName}`;
  }

  const methodName = test.methodName ? test.methodName.replace(/\[([^\]]+)]\([^)]+\)/g, '$1') : '';
  return `${test.packageName}.${test.className}.${methodName}`;
}

module.exports = {
  parseTestName,
  getTestKey,
  parseComment,
  prepareFirstRunData,
  mergeFlakyData
};
