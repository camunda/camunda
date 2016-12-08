export function includes(values, value) {
  return values
    .filter((other) => other === value)[0];
}
