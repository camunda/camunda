import chaiDom from 'chai-dom';
import chai from 'chai';
import {createEventsBus, runUpdate} from 'view-utils';

chai.use(chaiDom);

const html = `
  <div id="dom-testing-target"></div>
`;

export function mountTemplate(template) {
  document.body.innerHTML = html;

  const node = document.getElementById('dom-testing-target');
  const eventsBus = createEventsBus();
  const update = runUpdate.bind(
    null,
    template(node, eventsBus)
  );

  return {
    node,
    eventsBus,
    update
  };
}
