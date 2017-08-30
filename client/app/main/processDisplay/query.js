import {filterType as executedNodeType} from 'main/processDisplay/controls/filter/executedNode';

export function getFilterQuery(filter) {
  return {
    dates: filter
      .filter(entry => entry.type === 'startDate')
      .reduce((dates, entry) => {
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
      }, []),

    variables: filter
      .filter(entry => entry.type === 'variable')
      .map(entry => parseVariableFilter(entry.data)),

    executedFlowNodes: filter
      .filter(({type}) => type === executedNodeType)
      .reduce(
        ({andLinkedIds}, {data}) => {
          return {
            andLinkedIds: andLinkedIds.concat({
              orLinkedIds: data.map(({id}) => id)
            })
          };
        },
        {andLinkedIds: []}
      )
  };
}

export function parseVariableFilter(data) {
  return {
    name: data[0],
    type: data[1],
    operator: data[2],
    values: data[3]
  };
}
