import Viewer from 'bpmn-js/lib/NavigatedViewer';

export default function extractProcessDefinitionName(processDefinitionXml) {
  return new Promise(resolve => {
    const viewer = new Viewer();
    viewer.importXML(processDefinitionXml, () => {
      const rootElements = viewer.definitions.rootElements;
      const element = rootElements.find(e => e.$instanceOf('bpmn:Process'));
      if (element) {
        const processDefinitionName = element.name;
        resolve(processDefinitionName);
      }
    });
  });
}
