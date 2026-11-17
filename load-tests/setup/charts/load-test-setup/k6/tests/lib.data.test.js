// Unit tests for `scripts/lib.data.js`. The k6 `open()` function is replaced with a mock.
import { afterEach, describe, it, mock } from "node:test";
import assert from "node:assert/strict";

afterEach(() => {
  delete globalThis.__ENV;
  delete globalThis.open;
  mock.restoreAll();
});

// `lib.data.js` reads `__ENV.DATA_DIR` at import time: the random query string
// bypasses the module cache to forcefully re-import the module with a
// different `__ENV` value.
const importFresh = (dataDir) => {
  globalThis.__ENV = dataDir ? { DATA_DIR: dataDir } : {};
  globalThis.open = mock.fn((path) => `content of ${path}`);
  mock.method(console, "log", () => {});
  return import(`../scripts/lib.data.js?${Math.random()}`);
};

describe("loadData", () => {
  it("should read the file from the ../data directory by default", async () => {
    // given
    const { loadData } = await importFresh();

    // when
    const content = loadData("micro.bpmn");

    // then
    assert.equal(content, "content of ../data/micro.bpmn");
  });

  it("should read the file from the directory set in DATA_DIR", async () => {
    // given
    const { loadData } = await importFresh("/mnt/data");

    // when
    const content = loadData("micro.bpmn");

    // then
    assert.equal(content, "content of /mnt/data/micro.bpmn");
  });
});
