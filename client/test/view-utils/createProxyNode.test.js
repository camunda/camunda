import chai from 'chai';
import chaiDom from 'chai-dom';
import {createProxyNode} from 'view-utils/createProxyNode';

chai.use(chaiDom);

const {expect} = chai;

describe('createProxyNode', () => {
  let targetNode;
  let header;
  let footer;
  let startMarker;
  let proxyNode;
  let child;
  let textChild;

  beforeEach(() => {
    targetNode = document.createElement('div');

    header = document.createTextNode('Something before');
    targetNode.appendChild(header);

    startMarker = document.createComment('Start Marker');
    targetNode.appendChild(startMarker);

    footer = document.createTextNode('Something after');
    targetNode.appendChild(footer);

    proxyNode = createProxyNode(targetNode, startMarker);

    child = document.createElement('div');
    child.innerHTML = '<strong>child</strong>';

    textChild = document.createTextNode('child');
  });

  describe('attributes', () => {
    const attribute = 'attr';
    const value = 'val-1';

    it('should be able to add attribute to targetNode', () => {
      proxyNode.setAttribute(attribute, value);

      expect(targetNode.getAttribute(attribute)).to.eql(value);
    });

    it('should be able to see attributes added on targetNode', () => {
      targetNode.setAttribute(attribute, value);

      expect(proxyNode.getAttribute(attribute)).to.eql(value);
    });

    it('should be able to remove targetNode attribute', () => {
      targetNode.setAttribute(attribute, value);
      proxyNode.removeAttribute(attribute);

      expect(proxyNode.getAttribute(attribute)).to.eql(null);
      expect(targetNode.getAttribute(attribute)).to.eql(null);
    });
  });

  it('should be able to modify classList of targetNode', () => {
    const className = 'other-class';

    proxyNode.classList.add(className);

    expect(targetNode.classList.contains(className)).to.eql(true);
  });

  describe('childNodes', () => {
    it('should have empty childNodes list by default', () => {
      expect(proxyNode.childNodes).to.eql([]);
    });

    it('should not be possible to add node with childNodes', () => {
      proxyNode.childNodes.push(
        document.createTextNode('dd1')
      );

      expect(proxyNode.childNodes).to.eql([]);
      expect(targetNode).to.not.contain.text('dd1');
    });
  });

  describe('appendChild', () => {
    it('should add child in right place in targetNode', () => {
      proxyNode.appendChild(textChild);

      expect(targetNode).to.contain.text(textChild.data);
      expect(targetNode.childNodes[0]).to.eql(header);
      expect(targetNode.childNodes[1]).to.eql(startMarker);
      expect(targetNode.childNodes[2]).to.eql(textChild);
      expect(targetNode.childNodes[3]).to.eql(footer);
    });

    it('should add only one child to proxyNode childNodes array', () => {
      proxyNode.appendChild(textChild);

      expect(proxyNode.childNodes).to.eql([textChild]);
    });
  });

  describe('removeChild', () => {
    beforeEach(() => {
      proxyNode.appendChild(child);
    });

    it('should remove child from target node', () => {
      proxyNode.removeChild(child);

      expect(targetNode.childNodes.length).to.eql(3);
      expect(targetNode).to.contain.text(header.data);
      expect(targetNode).to.contain.text(footer.data);
      expect(targetNode).to.not.contain.text('child');
    });

    it('should remove child from proxy node', () => {
      proxyNode.removeChild(child);

      expect(proxyNode.childNodes.length).to.eql(0);
    });
  });

  describe('insertBefore', () => {
    beforeEach(() => {
      proxyNode.appendChild(child);
      proxyNode.insertBefore(textChild, child);
    });

    it('should insert text node before child in target node', () => {
      expect(targetNode.childNodes[2]).to.eql(textChild);
      expect(targetNode.childNodes[3]).to.eql(child);
    });

    it('should insert text node before child in proxy node', () => {
      expect(proxyNode.childNodes).to.eql([textChild, child]);
    });
  });

  describe('removeChildren', () => {
    let children;

    beforeEach(() => {
      proxyNode.appendChild(child);
      proxyNode.appendChild(textChild);

      children = proxyNode.removeChildren();
    });

    it('should return removed children', () => {
      expect(children).to.eql([child, textChild]);
    });

    it('should remove child nodes of proxy node from target node', () => {
      expect(targetNode).not.to.contain.text(textChild.data);
      expect(targetNode.querySelector('strong')).not.to.exist;
    });

    it('should remove child nodes of proxy node', () => {
      expect(proxyNode.childNodes).to.eql([]);
    });

    it('should not remove other target node children', () => {
      expect(targetNode).to.contain.text(header.data);
      expect(targetNode).to.contain.text(footer.data);
    });
  });

  describe('replaceChild', () => {
    beforeEach(() => {
      proxyNode.appendChild(child);
      proxyNode.replaceChild(textChild, child);
    });

    it('should have only new child in childNodes of proxy node', () => {
      expect(proxyNode.childNodes).to.eql([textChild]);
    });

    it('should not contain old child', () => {
      expect(targetNode.querySelector('strong')).not.to.exist;
    });

    it('should contain new child', () => {
      expect(targetNode).to.contain.text(textChild.data);
    });
  });

  describe('setStartMarker', () => {
    it('should append child after header, but before old start marker', () => {
      proxyNode.setStartMarker(header);
      proxyNode.appendChild(child);

      expect(targetNode.childNodes[0]).to.eql(header);
      expect(targetNode.childNodes[1]).to.eql(child);
      expect(targetNode.childNodes[2]).to.eql(startMarker);
    });
  });
});
