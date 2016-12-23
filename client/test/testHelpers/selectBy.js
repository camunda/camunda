export function selectByText(nodes, searchText) {
  return filterNodes(nodes, node => {
    const text = node.innerText;

    return text.indexOf(searchText) >= 0;
  });
}

export function selectByRegExp(nodes, regExp) {
  return filterNodes(nodes, node => {
    const text = node.innerText;

    return regExp.test(text);
  });
}

function filterNodes(nodes, predicate) {
  return Array.prototype.filter.call(nodes, predicate);
}
