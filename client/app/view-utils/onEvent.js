export function OnEvent({event, listener}) {
  return (node) => {
    let lastState;

    node.addEventListener(event, (event) => {
      listener({
        state: lastState,
        event,
        node
      });
    });

    return (state) => lastState = state;
  };
}
