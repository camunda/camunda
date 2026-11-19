// Unit tests for the wait for the prefill sentinel in `scripts/test.custom-model.js`. It has its
// own file because the sentinel is read from the environment when the script is imported.
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { loadK6Module } from "./helpers.js";

const { script, http, sleep, respondWith } = await loadK6Module(
  "../scripts/test.custom-model.js",
  {
    data: (name) => `content of ${name}`,
    env: { PREFILL_SENTINEL_ASSIGNEE: "prefill-done" },
  },
);

const searchesOf = () =>
  http.request.mock.calls
    .map((call) => ({
      url: call.arguments[1],
      body: JSON.parse(call.arguments[2]),
    }))
    .filter((request) => request.url.endsWith("/v2/user-tasks/search"));

describe("test.custom-model.js wait for the prefill", () => {
  it("should search again until the sentinel user task exists", () => {
    // given: the sentinel shows in the third search
    respondWith(
      { status: 200, json: () => [] },
      { status: 503, json: () => undefined },
      { status: 200, json: () => [{ userTaskKey: "sentinel" }] },
    );

    // when
    script.waitForPrefill();

    // then
    const searches = searchesOf();
    assert.equal(searches.length, 3);
    assert.deepEqual(searches[0].body.filter, { assignee: "prefill-done" });
    assert.equal(sleep.mock.callCount(), 2, "pauses between the searches");
  });
});
