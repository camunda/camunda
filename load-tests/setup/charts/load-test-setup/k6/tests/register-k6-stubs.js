// Resolve the k6 runtime modules to stub files, so that `mock.module()` can replace them.
// Loaded with `node --import`: see `make test` in the `k6` directory.
import { registerHooks } from "node:module";

const stubs = {
  k6: new URL("./stubs/k6.js", import.meta.url).href,
  "k6/http": new URL("./stubs/k6-http.js", import.meta.url).href,
};

registerHooks({
  resolve(specifier, context, nextResolve) {
    if (specifier in stubs) {
      return { url: stubs[specifier], shortCircuit: true };
    }
    return nextResolve(specifier, context);
  },
});
