export function pipe(...functions) {
  return (...args) => {
    const length = functions.length;
    let result = functions[0](...args);

    for (let i = 1; i < length; i++) {
      result = functions[i](result);
    }

    return result;
  };
}
