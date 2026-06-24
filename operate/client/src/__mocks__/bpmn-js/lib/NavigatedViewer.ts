/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const diObject = {set: vi.fn()};
const BPMN_NODES = [
  'bpmn\\:startEvent',
  'bpmn\\:endEvent',
  'bpmn\\:userTask',
  'bpmn\\:task',
  'bpmn\\:scriptTask',
  'bpmn\\:subProcess',
].join(',');

type Listener = (event: unknown) => void;

class EventBusMock {
  private listeners = new Map<string, Listener[]>();

  on = vi.fn((event: string, cb: Listener) => {
    const list = this.listeners.get(event) ?? [];
    list.push(cb);
    this.listeners.set(event, list);
  });

  off = vi.fn((event: string, cb: Listener) => {
    this.listeners.set(
      event,
      (this.listeners.get(event) ?? []).filter((listener) => listener !== cb),
    );
  });

  emit(event: string, payload: unknown) {
    this.listeners.get(event)?.forEach((cb) => cb(payload));
  }
}

const createMockFlowNode = (id: string) => ({
  id,
  type: 'bpmn:Task',
  di: diObject,
  businessObject: {
    id,
    $instanceOf: (type: string) => type === 'bpmn:FlowNode',
  },
});

const createMockedModules = (container: HTMLElement) => ({
  canvas: {
    zoom: vi.fn(),
    addMarker: vi.fn(),
    removeMarker: vi.fn(),
    resized: vi.fn(),
    getRootElement: vi.fn(() => container.innerHTML),
    findRoot: vi.fn(),
  },
  zoomScroll: {stepZoom: vi.fn()},

  elementRegistry: {
    getGraphics: vi.fn(() => ({
      querySelector: vi.fn(() => ({setAttribute: vi.fn()})),
      getBoundingClientRect: vi.fn(() => ({
        x: 0,
        y: 0,
        height: 0,
        width: 0,
      })),
      getBBox: vi.fn(() => ({x: 0, y: 0, height: 0, width: 0})),
    })),
    get: vi.fn((id: string) => createMockFlowNode(id)),
    forEach: vi.fn(() => {}),
    filter: vi.fn(() => []),
  },
  graphicsFactory: {update: vi.fn(() => {})},
  eventBus: new EventBusMock(),
  overlays: {
    add: vi.fn(
      (_: string, __: string, {html: children}: {html: HTMLElement}) => {
        container.appendChild(children);
      },
    ),
    remove: vi.fn(),
    clear: vi.fn(),
  },
});

function extractFlowNodes(xml: string) {
  const doc = new DOMParser().parseFromString(xml, 'text/xml');
  return Array.from(doc.querySelectorAll(BPMN_NODES))
    .map((el) => el.getAttribute('id'))
    .filter((id): id is string => id !== null)
    .map((id) => ({id}));
}

class Viewer {
  bpmnRenderer: unknown;
  container: HTMLElement;
  modules: ReturnType<typeof createMockedModules>;

  constructor(
    {
      container,
      bpmnRenderer,
    }: {container: HTMLElement; bpmnRenderer: unknown} = {
      container: document.createElement('div'),
      bpmnRenderer: {},
    },
  ) {
    this.container = container;
    this.bpmnRenderer = bpmnRenderer;
    this.modules = createMockedModules(container);
  }

  importDefinitions = vi.fn(() => Promise.resolve({}));

  importXML = vi.fn((xml: string) => {
    this.container.innerHTML = 'Diagram mock';
    const flowNodes = extractFlowNodes(xml);
    flowNodes.forEach(({id}) => {
      const element = createMockFlowNode(id);
      const node = document.createElement('div');
      node.setAttribute('data-testid', id);

      node.addEventListener('click', () => {
        this.modules.eventBus.emit('element.click', {element});
      });

      this.container.appendChild(node);
    });

    return Promise.resolve({});
  });

  detach = vi.fn();
  destroy = vi.fn();
  on = (event: string, cb: Listener) => {
    this.modules.eventBus.on(event, cb);
  };

  off = (event: string, cb: Listener) => {
    this.modules.eventBus.off(event, cb);
  };

  get = (module: keyof ReturnType<typeof createMockedModules>) =>
    this.modules[module];
}

export default Viewer;
