import {jsx} from 'view-utils';
import sinon from 'sinon';
import {mockFunction} from  './mockFunction';

export function createMockComponent(text) {
  const update = sinon.spy();
  const jsxTemplate = <div>{text}</div>;

  const template = mockFunction((node, eventsBus) => {
    return [
      update,
      jsxTemplate(node, eventsBus)
    ];
  });

  const constructor = mockFunction(() => {
    return template;
  });

  constructor.set('template', template);
  constructor.set('update', update);

  constructor.getEventsBus = (index) => {
    return template.calls[index][1];
  };

  return constructor;
}
