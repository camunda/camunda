export function flatten(array) {
  if (Array.isArray(array)) {
    return array.reduce((array, item) => {
      return array.concat(
        flatten(item)
      );
    }, []);
  }

  return array;
}
