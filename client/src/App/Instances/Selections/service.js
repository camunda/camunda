export function getParentFilter(filter) {
  const appliedFilters = Object.keys(filter);
  if (appliedFilters.includes('incidents' || 'active')) return {running: true};
  if (appliedFilters.includes('completed' || 'canceled'))
    return {finished: true};
}
