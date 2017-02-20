export function clickElement({type, name, id}, {heatmap: {data}}, nodes) {
  if (type === 'bpmn:EndEvent') {
    nodes.name.textContent = name || id;
    nodes.counter.textContent = Math.round(getRatio(data[id], data) * 1000) / 10;
    return true;
  }

  return false;
}

function getRatio(value, allValues) {
  return value / Math.max.apply(null, Object.values(allValues));
}
