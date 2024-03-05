# InstancesPollContext

The InstancesPollContext is a React context we use as a layer to handles the logic for polling on Instances List page.

## When do we poll?

On the Instances List page the user can trigger operations:

- at Instance level, see https://github.com/camunda/camunda-operate/blob/master/client/src/App/Instances/ListView/List/List.js#L266
- at Selection level, see https://github.com/camunda/camunda-operate/blob/master/client/src/App/Instances/Selections/SelectionList/SelectionList.js#L88

Each time a user triggers operations on instances we would make a request to fetch only these instances in order to see when the corresponding operations have completed.

## How long do we poll?

The first poll request is made with the instances the user has started operations on. Every other 5 seconds, from the moment a poll request is finished, we continue to make the request only with the instances that still have active operations. When no instance has active operations we stop polling.

## How do we poll?

At Instance level, on the ListView, when the user starts operations we poll for the corresponding instances as long as they are in view. This means that we stop polling when:

- the user changes the page, the filter or the sorting and the instances are no longer in view.
- the user collapses the ListView and instances with active operations are no longer in view.

At Selection level, when the user starts operations we only poll for the visible instances per selection (max 10 instances). This means that if a selection has 30 instances and we retry all, we only check for operations completion for the 10 instances visible in the Selection preview.

## Updating

As we poll we check if any operation has been completed and if we have still have ongoing operations.

When we have completed operations we update the page to reflect the new state.
When we have active operations we continue polling until they are completed.

### Updating ListView

We refetch the instances list, which results in:

- the instance display the new state, or action state(failed)
- the instance is no longer in the list, as it falls outside the filter
- the filter & header counts are updated

### Updating diagram

If a diagram is visible, we also refetch the statistics in order to reflect the new counts

### Updating Selections

We refetch the instances that are visible in the Selections summaries.

### Synched update

If an operation is started from the ListView, and the instance is also present in a Selection, we also update the Selections list.

If an operation is started on a Selection, we also update the ListView, as the header counts and statistics could be outdated once the operations complete.

## How does the InstancesPollContext work?

The InstancesPollProvider will receive as props:

- methods to refresh the Instances List (onProcessInstancesRefresh) & the Selections (onSelectionsRefresh)
- the ids that are present in Instances List (visibleIdsInListView) & in Selections (visibleIdsInListPanel). Based on these ids it decides what part of the page to update

The InstancesPollProvider will send to the consumers the props:

- addIds: function to add the ids of the instances the operations have started on
- removeIds: function to remove the ids, for example when they are no longer in view

As long as the InstancesPollProvider has ids in it's state, it will poll and check as explained in Updating section
