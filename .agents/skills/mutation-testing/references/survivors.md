# Judging survivors

## Decide each survivor

Read the production code around the line and the tests that ran, then pick one:

- **Test gap**: the behaviour is real and observable but no test asserts it. Write or extend a test
  that fails under the mutation.
- **Equivalent**: the mutation cannot change behaviour. Write no test.
- **Low value**: it only affects logging, metrics, `toString`, naming, or lifecycle wiring. Write no
  test.
- **Real bug**: a test that encodes clearly intended behaviour (from the Javadoc, neighbouring
  tests, or call sites) fails against the current, unmutated code. Keep the failing output and
  report it.

## Spotting equivalent mutants

- Conditional boundary (`<` to `<=`) where the boundary value cannot occur: an earlier check, the
  type, or every caller excludes it.
- A removed call or void-method mutant whose effect nobody can observe: a redundant
  initialisation, a cache hint, a second call that repeats the first.
- A negated or constant-returned value on a branch whose two sides do the same, or whose result is
  immediately overwritten.
- An increment or arithmetic mutant on a counter that is only logged.
- A mutated default that every caller overrides.

Before calling a survivor equivalent, name the reason in one sentence. If you cannot, treat it as a
test gap.

## Safety-relevant in DS code

These deserve the most attention, because a silent change corrupts state or loses data:

- validations and guards (preconditions, argument and state checks)
- error and exception paths (swallowing, wrong exception, skipped failure handling)
- persistence, flush and fsync (data reported durable without being written)
- term, index, version and quorum comparisons (off-by-one, inverted comparison, wrong operand)
- resource cleanup (close, release, unlock, deregister)

In `zeebe/dynamic-config`, state, transformers, changes and serializers decide what the cluster
configuration becomes; lifecycle wiring, logging and metrics do not.

## Noise and pitfalls

Measured on `:zeebe-cluster-config-impl`:

- Two identical runs disagreed on 19 of 2,224 mutants, 13 of them flipping between `SURVIVED` and
  `TIMED_OUT`, mostly in asynchronous or actor code. Do not trust a single `SURVIVED` or
  `TIMED_OUT` in such code; re-run before concluding.
- Lowering the timeout to 1500 ms turned 27 survivors into `TIMED_OUT` without a measurable
  speedup, hiding gaps. Never lower it.
- Descartes (`pit.engine=descartes`) does extreme mutation: it removes method bodies or returns
  constants, giving about half the mutants and a run about 37% faster on impl. It reports
  pseudo-tested methods, not fine-grained gaps. In Maven it lives in a profile because its filters
  on the classpath also drop mutants from regular runs (1,095 instead of 1,166 on the api module).
- Line-coverage export and PIT history are off on purpose: the export is about 163 MB and history
  needs a commercial plugin. Do not enable them.
