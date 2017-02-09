import {expect} from 'chai';
import {mountTemplate} from 'testHelpers';
import {withSockets, Socket, Children, jsx, noop} from 'view-utils';

describe('<Socket />', () => {
  it('should produce template function with socket property', () => {
    const name = 'name-1';
    const children = 'children';
    const template = Socket({name, children});

    expect(typeof template).to.eql('function');
    expect(template.socket.name).to.eql(name);
    expect(template.socket.children).to.eql(children);
  });
});

describe('withSockets', () => {
  it('should throw exception when children templates do not have socket properties', () => {
    const Component = withSockets(noop);

    expect(() => {
      Component({
        children: ['blah']
      });
    }).to.throw;
  });

  describe('in interaction with Socket and Children', () => {
    let Host;
    let node;

    beforeEach(() => {
      Host = withSockets(({sockets: {head, body}}) => <div className="host">
        <header>
          <Children children={head} />
        </header>
        <div className="host-body">
          <Children children={body} />
        </div>
      </div>);

      ({node} = mountTemplate(<Host>
        <Socket name="head">
          <h1>Head</h1>
        </Socket>
        <Socket name="body">
          body!
          <b>jaj</b>
        </Socket>
      </Host>));
    });

    it('should insert head socket into host header', () => {
      expect(node.querySelector('.host > header > h1')).to.contain.text('Head');
    });

    it('should insert body socket into host .body', () => {
      expect(node.querySelector('.host > .host-body')).to.contain.text('body!');
      expect(node.querySelector('.host > .host-body > b')).to.contain.text('jaj');
    });
  });
});
