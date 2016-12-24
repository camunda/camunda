export function OnEvent({event, listener}) {
  return (node) => {
    let lastState;

    if (typeof event === 'string') {
      addEventListener(event);
    } else if (Array.isArray(event)) {
      event.forEach(addEventListener);
    }

    return (state) => lastState = state;

    function addEventListener(event) {
      node.addEventListener(event, (event) => {
        listener({
          state: lastState,
          event,
          node
        });
      });
    }
  };
}
