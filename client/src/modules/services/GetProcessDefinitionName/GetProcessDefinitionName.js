import Viewer from 'bpmn-js/lib/NavigatedViewer';

export default function extractProcessDefinitionName(processDefinitionXml) {
  return new Promise(resolve => { 
    const viewer = new Viewer();
    viewer.importXML(processDefinitionXml, () => {
      const elementRegistry = viewer.get('elementRegistry');
      const element = elementRegistry.filter(e => (e.type === 'bpmn:Participant'));
      if( element.length>0) {
        const name = element[0].businessObject.name;
        resolve({processDefinitionName: name});
      }
       
    }); 
    
  });
}
