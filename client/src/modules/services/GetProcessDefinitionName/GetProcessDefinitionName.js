import Viewer from 'bpmn-js/lib/NavigatedViewer';

export default function extractProcessDefinitionName(processDefinitionXml) {
  return new Promise(resolve => {
    const viewer = new Viewer();
    viewer.importXML(processDefinitionXml, () => {
      resolve(viewer.definitions.rootElements.find(e => e.$instanceOf('bpmn:Process')).name);
    });
  });
}
