// Code here might seem a little bit strange, but javascript does not like when one function is called
// with different arguments, so to make life a little bit easier for engine, different functions for
// different inputs are used. This results in a little bit of code repetition, but it is easier to optimize
// for JS engine.

export function runUpdate(update, state) {
  if (Array.isArray(update)) {
    return updateArray(update, state)
  }

  if (update.update) {
    return updateObject(update, state);
  }

  return updateFunction(update, state);
}

function updateArray(updates, state) {
  updates.forEach(update => {
    if (Array.isArray(update)) {
      return updateArray(update, state);
    } else if (update.update) {
      return updateObject(update, state);
    }

    updateFunction(update, state);
  });
}

function updateObject({update}, state) {
  if (Array.isArray(update)) {
    return updateArray(update, state);
  }

  updateFunction(update, state);
}

function updateFunction(update, state) {
  return update(state);
}
