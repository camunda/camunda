const getMaxPage = (total, perPage) => Math.ceil(total / perPage);

const isAnyInstanceSelected = selection => {
  const {ids, excludeIds, ...rest} = selection;

  if (!!Object.keys(rest).length || !!ids.size) {
    return true;
  } else {
    return false;
  }
};

export {getMaxPage, isAnyInstanceSelected};
