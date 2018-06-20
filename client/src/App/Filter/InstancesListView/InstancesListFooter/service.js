export function getRange(focus, max) {
  let start = Math.max(focus - 2, 1);
  let end = Math.min(start + 4, max);
  if (max - focus < 2) {
    start = Math.max(end - 4, 1);
  }

  const pages = [];
  for (let i = start; i <= end; i++) {
    pages.push(i);
  }

  return pages;
}
