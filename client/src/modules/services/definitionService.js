export function extractDefinitionName(key, xml) {
  return (
    new DOMParser()
      .parseFromString(xml, 'text/xml')
      .querySelector(`[id="${key}"]`)
      .getAttribute('name') || key
  );
}
