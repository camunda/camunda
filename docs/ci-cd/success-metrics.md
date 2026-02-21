# Success Metrics

> Back to [CI/CD Improvement Plan](./README.md)

| Metric | Current | Phase 0+1 (Wk 1-6) | Phase 2+3 (Wk 8-16) | Phase 4+5 (CD) |
|--------|---------|---------------------|----------------------|----------------|
| **PR wall-clock time** | ~55 min | ~45 min | ~25 min | ~20 min |
| **Compute cost per PR** | Baseline | -30% | -55% | -65% |
| **Flaky test rate** | Unknown (masked by 3x retries) | <10% (measured) | <5% | <2% |
| **CI retriggers per PR** | Multiple (daily pain) | <1 per PR | ~0 | 0 |
| **`rerunFailingTestsCount`** | 3 | 2 | 1 | 0-1 |
| **Local `mvn install -Dquickly`** | ~5-10 min | ~3-5 min | ~2-3 min | <2 min |
| **Tests disabled for flakiness** | 139+ files | <80 (triaged) | <30 | <10 |
| **Devs running tests locally** | Rarely | Some | Most | Standard |
| **Time to deploy after merge** | ~20 min | ~15 min | ~10 min | <10 min |
| **Main branch red rate** | Unknown | Measured | <5% | <1% |
