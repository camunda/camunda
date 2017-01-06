import {expect} from 'chai';
import sinon from 'sinon';
import {jsx, __set__, __ResetDependency__} from 'view-utils/jsx';
import {createEventsBus} from 'view-utils';

describe('jsx', () => {
  let childNode;
  let textNode;
  let node;
  let $document;

  beforeEach(() => {
    childNode = createNodeMock();
    textNode = 'text-node';

    $document = {
      createElement: sinon.stub().returns(childNode),
      createTextNode: sinon.stub().returns(textNode)
    };
    __set__('$document', $document);

    node = createNodeMock();
  });

  afterEach(() => {
    __ResetDependency__('$document');
  });

  it('should handle component constructor function', () => {
    const element = sinon.spy();
    const attributes = {
      type: 'type-2'
    };
    const children = ['c1', 'c2'];

    jsx(element, attributes, ...children);

    expect(element.calledWith({
      children,
      ...attributes
    })).to.eql(true);
  });

  it('should be possible to override children with attribute for component', () => {
    const element = sinon.spy();
    const attributes = {
      type: 'type-2',
      children: 'children'
    };
    const children = ['c1', 'c2'];

    jsx(element, attributes, ...children);

    expect(element.calledWith(attributes)).to.eql(true);
  });

  it('should return template created by component', () => {
    const template = 'template';
    const element = sinon.stub().returns(template);

    expect(jsx(element)).to.eql(template);
  });

  describe('html element template function', () => {
    const element = 'div';
    let attributes;
    let childUpdate;
    let childTemplate;
    let children;
    let template;
    let eventsBus;
    let updates;

    beforeEach(() => {
      attributes = {
        id: 'id-1'
      };
      childUpdate = sinon.spy();
      childTemplate = sinon.stub().returns(childUpdate);
      children = ['a1', childTemplate];
      template = jsx(element, attributes, ...children);
      eventsBus = createEventsBus();
      updates = template(node, eventsBus);
    });

    it('should be a function', () => {
      expect(typeof template).to.eql('function');
    });

    it('should return array with childUpdate', () => {
      expect(childTemplate.calledWith(childNode)).to.eql(true);

      expect(updates[0].update).to.eql(childUpdate);
    });

    it('should create and append static element', () => {
      expect($document.createElement.calledWith(element)).to.eql(true, `expected ${element} to be created`);
      expect(node.appendChild.calledWith(childNode)).to.eql(true, `expected ${element} to be appended to node`);
    });

    it('should set attributes on element node', () => {
      expect(childNode.setAttribute.calledWith('id', attributes.id)).to.eql(true);
    });

    it('should pass child events bus to childTemplate', () => {
      const [, childEventsBus] = childTemplate.firstCall.args;
      const eventName = 'event-203';
      const listener = sinon.spy();
      const data = {d: 204};

      childEventsBus.on(eventName, listener);
      eventsBus.fireEvent(eventName, data);

      expect(listener.calledWith({
        name: eventName,
        data,
        stopped: false
      }));
    });

    it('should create and append text node to element node', () => {
      expect($document.createTextNode.calledWith(children[0]))
        .to.eql(true, `expected child node with '${children[0]}' to be created`);
      expect(childNode.appendChild.calledWith(textNode))
        .to.eql(true, 'expected text node to be appended to element node');
    });
  });
});

function createNodeMock() {
  return {
    appendChild: sinon.spy(),
    setAttribute: sinon.spy()
  };
}
