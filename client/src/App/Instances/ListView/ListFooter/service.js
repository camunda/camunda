const getMaxPage = (total, perPage) => Math.ceil(total / perPage);

const isAnyInstanceSelected = selection => {
  const {all, ids} = selection;
  return all || ids.length > 0;
};

export {getMaxPage, isAnyInstanceSelected};
