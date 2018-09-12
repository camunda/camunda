const getMaxPage = (total, perPage) => Math.ceil(total / perPage);

const isAnyInstanceSelected = selection => {
  const {ids, excludeIds, ...filter} = selection;

  return !!Object.keys(filter).length || !!ids.size;
};

export {getMaxPage, isAnyInstanceSelected};
