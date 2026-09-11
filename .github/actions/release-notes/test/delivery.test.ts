import assert from 'node:assert/strict';
import test from 'node:test';
import { closesIssueNumbers } from '../src/delivery';
import type { DeliveryInput } from '../src/delivery';
import type { IssueClosure } from '../src/resolve';

function input(overrides: Partial<DeliveryInput> = {}): DeliveryInput {
  return { prNumber: 101, issueNumbers: [500], deliveryPath: 'direct', declaredCloses: [], ...overrides };
}

function closures(entries: Record<number, Partial<IssueClosure>>): Map<number, IssueClosure> {
  return new Map(
    Object.entries(entries).map(([number, closure]) => [
      Number(number),
      { closed: true, stateReason: 'COMPLETED', closerPrNumber: null, ...closure },
    ]),
  );
}

test('the pull request GitHub recorded as the closer closes the issue', () => {
  // given
  const closure = closures({ 500: { closerPrNumber: 101 } });

  // when
  const closes = closesIssueNumbers(input(), closure);

  // then
  assert.deepEqual(closes, [500]);
});

test('a recorded closer that is another pull request outranks this one’s own closing keyword', () => {
  // given — the split-delivery case: #101 wrote `closes #500`, #102 actually closed it
  const closure = closures({ 500: { closerPrNumber: 102 } });

  // when
  const closes = closesIssueNumbers(input({ declaredCloses: [500] }), closure);

  // then
  assert.deepEqual(closes, []);
});

test('an issue with no recorded closer falls back to the declared closing keyword', () => {
  // given — merged into stable/8.9, where GitHub fires no close event at all
  const closure = closures({ 500: { closed: true, closerPrNumber: null } });

  // when
  const closes = closesIssueNumbers(input({ declaredCloses: [500] }), closure);

  // then
  assert.deepEqual(closes, [500]);
});

test('an issue absent from the closure map falls back to the declared closing keyword', () => {
  // given — the number resolved to a pull request, or the issue is gone
  // when
  const closes = closesIssueNumbers(input({ declaredCloses: [500] }), new Map());

  // then
  assert.deepEqual(closes, [500]);
});

test('an issue neither closed nor declared is only a contributor', () => {
  // given
  const closure = closures({ 500: { closed: false, stateReason: null } });

  // when
  const closes = closesIssueNumbers(input(), closure);

  // then
  assert.deepEqual(closes, []);
});

test('an issue closed as not planned is never reported as delivered', () => {
  // given — the keyword says otherwise, but GitHub recorded it as abandoned
  const closure = closures({ 500: { stateReason: 'NOT_PLANNED', closerPrNumber: 101 } });

  // when
  const closes = closesIssueNumbers(input({ declaredCloses: [500] }), closure);

  // then
  assert.deepEqual(closes, []);
});

test('an issue closed as a duplicate is never reported as delivered', () => {
  // given
  const closure = closures({ 500: { stateReason: 'DUPLICATE' } });

  // when
  const closes = closesIssueNumbers(input({ declaredCloses: [500] }), closure);

  // then
  assert.deepEqual(closes, []);
});

test('a backport hop delivers every issue it is attributed to, keyword or not', () => {
  // given — the bot writes no keyword and a stable merge fires no close event,
  // so both signals are blank; the original pull request closed the issue
  const closure = closures({ 500: { closerPrNumber: 900 } });

  // when
  const closes = closesIssueNumbers(input({ deliveryPath: 'backportHop', issueNumbers: [500, 501] }), closure);

  // then
  assert.deepEqual(closes, [500, 501]);
});

test('each issue of a multi-issue pull request is decided on its own', () => {
  // given
  const closure = closures({
    500: { closerPrNumber: 101 },
    501: { closerPrNumber: 102 },
    502: { closed: false, stateReason: null },
  });

  // when
  const closes = closesIssueNumbers(input({ issueNumbers: [500, 501, 502], declaredCloses: [502] }), closure);

  // then
  assert.deepEqual(closes, [500, 502]);
});
