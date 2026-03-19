# engineer-stream-8 Summary

**Timestamp:** 2026-03-19T10:12:59.666Z

**Fix applied:**

`getLatestPosition()` was returning `lastPos` (the last *written* position) instead of `lastPos + 1` (the *tail* — the position at which the next event would be written). This contradicted its own Javadoc and diverged from `tailPosition()` which correctly adds `+1`. Updated the implementation and renamed the test method to `shouldReturnTailPosition` with the corrected expectation of `43` when `seekToEnd()` returns `42`.
