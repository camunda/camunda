export function Socket({name, children}) {
  const template = () => {};

  template.socket = {
    name,
    children
  };

  return template;
}

export function withSockets(Component) {
  return ({children, ...props}) => {
    assertSockets(children);

    const sockets = children.reduce((sockets, {socket: {name, children}}) => {
      return {
        ...sockets,
        [name]: children
      };
    }, {});

    return Component({
      ...props,
      sockets
    });
  };
}

function assertSockets(children) {
  if (children.some(({socket}) => !socket)) {
    throw new Error('Expected all children template to be instances of Socket component');
  }
}
