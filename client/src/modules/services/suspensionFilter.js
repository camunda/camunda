export function containsSuspensionFilter(filterData) {
  const filters = filterData.map(filter => filter.type);

  return (
    filters.includes('suspendedInstancesOnly') || filters.includes('nonSuspendedInstancesOnly')
  );
}
