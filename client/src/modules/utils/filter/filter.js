export function getFilterQueryString(selection) {
  return `?filter=${JSON.stringify(selection)}`;
}
