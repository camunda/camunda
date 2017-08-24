import {jsx, List, Text, OnEvent, withSelector} from 'view-utils';

export const ExecutedNodeFilter = withSelector(({onDelete}) => {
  return <span>
    <button type="button" className="btn btn-link btn-xs pull-right">
      <OnEvent event="click" listener={onDelete} />
      ×
    </button>
    <span>
      Flow nodes &nbsp;
      <List>
        <span className="executed-node badge">
          <Text property={getName}/>
        </span>
      </List>
    </span>
  </span>;

  function getName({name, id}) {
    return name || id;
  }
});
