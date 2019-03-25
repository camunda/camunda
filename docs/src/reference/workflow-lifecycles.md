# Workflow Lifecycles

In Zeebe, the workflow execution is represented internally by events of type `WorkflowInstance`. The events are written to the log stream and can be observed by an exporter.

Each event is one step in a workflow instance. All events of one workflow instance have the same `workflowInstanceKey`.

Events which belongs to the same element instance (e.g. a task) have the same `key`. The element instances have different lifecycles depending on the type of element.

## (Sub-)Process/Activity/Gateway Lifecycle

![activity lifecycle](/reference/activity-lifecycle.png)

## Event Lifecycle

![event lifecycle](/reference/event-lifecycle.png)

## Sequence Flow Lifecycle

![sequence flow lifecycle](/reference/pass-through-lifecycle.png)

## Example

![order process](/bpmn-workflows/workflow.png)

<table>
    <tr>
        <th>Intent</th>
        <th>Element Id</th>
        <th>Element Type</th>
    </tr>
    <tr>
        <td>ELEMENT_ACTIVATING</td>
        <td>order-process</td>
        <td>process</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATED</td>
        <td>order-process</td>
        <td>process</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATING</td>
        <td>order-placed</td>
        <td>start event</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATED</td>
        <td>order-placed</td>
        <td>start event</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETING</td>
        <td>order-placed</td>
        <td>start event</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETED</td>
        <td>order-placed</td>
        <td>start event</td>
    <tr>
    <tr>
        <td>SEQUENCE_FLOW_TAKEN</td>
        <td>to-collect-money</td>
        <td>sequence flow</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATING</td>
        <td>collect-money</td>
        <td>task</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATED</td>
        <td>collect-money</td>
        <td>task</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETING</td>
        <td>collect-money</td>
        <td>task</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETED</td>
        <td>collect-money</td>
        <td>task</td>
    <tr>
    <tr>
        <td>SEQUENCE_FLOW_TAKEN</td>
        <td>to-fetch-items</td>
        <td>sequence flow</td>
    <tr>
    <tr>
        <td>...</td>
        <td>...</td>
        <td>...</td>
    <tr>
    <tr>
        <td>SEQUENCE_FLOW_TAKEN</td>
        <td>to-order-delivered</td>
        <td>sequence flow</td>
    <tr>
    <tr>
        <td>EVENT_ACTIVATING</td>
        <td>order-delivered</td>
        <td>end event</td>
    <tr>
    <tr>
        <td>EVENT_ACTIVATED</td>
        <td>order-delivered</td>
        <td>end event</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETING</td>
        <td>order-delivered</td>
        <td>end event</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETED</td>
        <td>order-delivered</td>
        <td>end event</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETING</td>
        <td>order-placed</td>
        <td>process</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETED</td>
        <td>order-placed</td>
        <td>process</td>
    <tr>
</table>
