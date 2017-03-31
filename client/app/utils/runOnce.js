export function runOnce(func) {
  let executed = false;

  return (...args) => {
    if (!executed) {
      executed = true;

      func(...args);
    }
  };
}
