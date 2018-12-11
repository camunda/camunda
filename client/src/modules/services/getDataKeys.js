export default function getDataKeys(data) {
  // We need to do explicit Object coercion thanks to IE
  // eslint-disable-next-line no-new-object
  return Object.keys(new Object(data));
}
