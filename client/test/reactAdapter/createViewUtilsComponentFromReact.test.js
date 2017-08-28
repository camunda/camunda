import {expect} from 'chai';
import {mountTemplate} from 'testHelpers';
import {DESTROY_EVENT} from 'view-utils';
import sinon from 'sinon';
import React from 'react';
import {createViewUtilsComponentFromReact} from 'reactAdapter';

describe('createViewUtilsComponentFromReact', () => {
  let componentDidMountSpy;
  let componentWillUnmountSpy;
  let renderSpy;
  let ReactComponent;
  let ViewUtilsComponent;
  let node;
  let update;
  let eventsBus;

  beforeEach(() => {
    componentDidMountSpy = sinon.spy();
    componentWillUnmountSpy = sinon.spy();
    renderSpy = sinon.spy();

    ReactComponent = class extends React.Component {
      componentDidMount() {
        componentDidMountSpy();
      }

      componentWillUnmount() {
        componentWillUnmountSpy();
      }

      render() {
        renderSpy(this.props);

        return React.createElement(
          'div',
          {
            className: 'some-element'
          },
          'some text'
        );
      }
    };

    ViewUtilsComponent = createViewUtilsComponentFromReact('div', ReactComponent);

    ({node, update, eventsBus} = mountTemplate(
      ViewUtilsComponent({
        a: 1
      })
    ));
  });

  it('should render some text', () => {
    expect(node).to.contain.text('some text');
  });

  it('should pass ViewUtilsComponent attributes as ReactComponent properties', () => {
    expect(renderSpy.firstCall.args[0]).to.have.property('a', 1);
  });

  it('should add div with some-element class', () => {
    expect(node).to.contain('div.some-element');
  });

  it('should pass state property to react component', () => {
    update({b: 22});

    expect(renderSpy.callCount).to.eql(2);
    expect(renderSpy.lastCall.args[0]).to.have.property('b', 22);
  });

  it('should call unmount methods of react component on destroy event', () => {
    eventsBus.fireEvent(DESTROY_EVENT);

    expect(componentDidMountSpy.calledOnce).to.eql(true);
    expect(componentWillUnmountSpy.calledOnce).to.eql(true);
  });
});
