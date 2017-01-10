import bpmnFile from './bpmnFile';

export function getHeatmapData(id) {
  return Promise.resolve({
    StartEvent_1: 2,
    Task_04xnoiz: 4,
    Task_1wjnddq: 3,
    Task_1b0sjb2: 1,
    ExclusiveGateway_07mu6ot: 4,
    ExclusiveGateway_1qeh21h: 4,
    EndEvent_0145qhl: 4
  });
}

export function getDiagramXml(id) {
  return Promise.resolve(bpmnFile);
}
