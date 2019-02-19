export function flatten(ctx = '', suffix = () => '') {
  return (flat, entry) => {
    if (entry.columns) {
      // nested column, flatten recursivly with augmented context
      return flat.concat(entry.columns.reduce(flatten(ctx + entry.label, suffix), []));
    } else {
      // normal column, return current context with optional suffix
      return flat.concat(ctx + suffix(entry));
    }
  };
}
