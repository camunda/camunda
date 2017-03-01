export function getFilterQuery(filter) {
  return {
    dates: filter.reduce((dates, entry) => {
      return dates.concat([
        {
          type: 'start_date',
          operator: '>=',
          value : entry.data.start,
          lowerBoundary : true,
          upperBoundary : true
        },
        {
          type: 'start_date',
          operator: '<=',
          value : entry.data.end,
          lowerBoundary : true,
          upperBoundary : true
        }
      ]);
    }, [])
  };
}
