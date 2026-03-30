import { readFileSync } from 'fs';

const xml = readFileSync('test-results/junit-report.xml', 'utf8');
const failures = xml.split('<failure message="');
failures.shift(); // remove preamble

const results = [];
for (const f of failures) {
  const testName = f.split('"')[0];
  const block = f.split('</failure>')[0];
  
  const expectedMatch = block.match(/Expected[^:]*:\s*(.+)/);
  const receivedMatch = block.match(/Received[^:]*:\s*(.+)/);
  const timeoutMatch = block.match(/Timeout/);
  
  const expected = expectedMatch ? expectedMatch[1].trim().substring(0, 50) : '?';
  const received = receivedMatch ? receivedMatch[1].trim().substring(0, 50) : timeoutMatch ? 'TIMEOUT' : '?';
  
  results.push({ testName: testName.substring(0, 80), expected, received });
}

// Categorize
const categories = {};
for (const r of results) {
  let cat;
  if (r.received === 'TIMEOUT') cat = 'TIMEOUT';
  else if (r.expected.startsWith('400') && r.received.startsWith('200')) cat = 'Expected 400, got 200 (invalid input accepted)';
  else if (r.expected.startsWith('400') && r.received.startsWith('500')) cat = 'Expected 400, got 500 (server error)';
  else if (r.expected.startsWith('200') && r.received.startsWith('404')) cat = 'Expected 200, got 404 (endpoint not found)';
  else if (r.expected.startsWith('204') && r.received.startsWith('400')) cat = 'Expected 204, got 400';
  else if (r.expected.startsWith('404') && r.received.startsWith('400')) cat = 'Expected 404, got 400';
  else if (r.received.includes('Failed to read request')) cat = 'Failed to read request';
  else if (r.received.includes('INVALID_ARGUMENT')) cat = 'Error message mismatch (INVALID_ARGUMENT)';
  else cat = `E:${r.expected.substring(0,30)} R:${r.received.substring(0,30)}`;
  
  if (!categories[cat]) categories[cat] = [];
  categories[cat].push(r.testName);
}

console.log('=== FAILURE CATEGORIES ===\n');
for (const [cat, tests] of Object.entries(categories).sort((a,b) => b[1].length - a[1].length)) {
  console.log(`[${tests.length}] ${cat}`);
  for (const t of tests) {
    console.log(`    ${t}`);
  }
  console.log();
}
