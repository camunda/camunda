export function incompatibleFilters(filterData) {
  const filters = filterData.map(filter => filter.type);

  return (
    ['completedInstancesOnly', 'runningInstancesOnly'].every(val => filters.includes(val)) ||
    ['canceledInstancesOnly', 'runningInstancesOnly'].every(val => filters.includes(val)) ||
    ['endDate', 'runningInstancesOnly'].every(val => filters.includes(val))
  );
}
