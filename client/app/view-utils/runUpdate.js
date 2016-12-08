export function runUpdate(update, state) {
  if (Array.isArray(update)) {
    for (let childUpdate of update) {
      runUpdate(childUpdate, state);
    }

    return;
  }

  if (update.update) {
    return runUpdate(update.update, state);
  }

  return update(state);
}
