# Multi-Instance

The following activities can be marked as multi-instance:

* [Service Tasks](/bpmn-workflows/service-tasks/service-tasks.html)
* [Receive Tasks](/bpmn-workflows/receive-tasks/receive-tasks.html)
* [Embedded Subprocesses](/bpmn-workflows/embedded-subprocesses/embedded-subprocesses.html)
* [Call Activities](/bpmn-workflows/call-activities/call-activities.html)

A multi-instance activity is executed multiple times - once for each element of a given collection (like a _foreach_ loop in a programming language).

![multi-instance](/bpmn-workflows/multi-instance/multi-instance-example.png)

On the execution level, a multi-instance activity has two parts: a **multi-instance body** and an inner activity. The multi-instance body is the container for all instances of the inner activity.

When the activity is entered, the multi-instance body is activated and one instance for every element of the `inputCollection` is created (sequentially or in parallel). When all instances are completed, the body is completed and the activity is left.

## Sequential vs. Parallel

A multi-instance activity is executed either sequentially or in parallel (default). In the BPMN, a sequential multi-instance activity is displayed with 3 horizontal lines at the bottom. A parallel one with 3 vertical lines.

In case of a **sequential** multi-instance activity, the instances are executed one-by-one. When one instance is completed then a new instance is created for the next element in the `inputCollection`.

![sequential multi-instance](/bpmn-workflows/multi-instance/multi-instance-sequential.png)

In case of a **parallel** multi-instance activity, all instances are created when the multi-instance body is activated. The instances are executed concurrently and independently from each other.

![parallel multi-instance](/bpmn-workflows/multi-instance/multi-instance-parallel.png)

## Defining the Collection to Iterate over

A multi-instance activity **must** have an `inputCollection` expression that defines the collection to iterate over (e.g. `= items`). Usually, it [accesses a variable](/reference/expressions.html#access-variables) of the workflow instance that holds the collection. The expression is evaluated on activating the multi-instance body. It must result in an `array` of any type (e.g. `["item-1", "item-2"]`).

In order to access the current element of the `inputCollection` value within the instance, the multi-instance activity can define the `inputElement` variable (e.g. `item`). The element is stored as a local variable of the instance under the given name.

If the `inputCollection` value is **empty** then the multi-instance body is completed immediately and no instances are created. It behaves like the activity is skipped.

## Collecting the Output

The output of a multi-instance activity (e.g. the result of a calculation) can be collected from the instances by defining the `outputCollection` **and** the `outputElement` expression.

`outputCollection` defines the name of the variable under which the collected output is stored (e.g. `results`). It is created as a local variable of the multi-instance body and gets updated when an instance is completed. When the multi-instance body is completed, the variable is propagated to its parent scope.

`outputElement` is an expression that defines the output of the instance (e.g. `= result`). Usually, it [accesses a variable](/reference/expressions.html#access-variables) of the instance that holds the output value. If the expression only accesses a variable or a nested property then it is created as **local variable** of the instance. This variable should be updated with the output value, for example, by a job worker providing a variable with the name `result`. Since the variable is defined as a local variable, it is not propagated to its parent scope and is only visible within the instance.

When the instance is completed, the `outputElement` expression is evaluated and the result is inserted into the `outputCollection` at the same index as the `inputElement` of the `inputCollection`. So, the order of the `outputCollection` is determined and matches to the `inputCollection`, even for parallel multi-instance activities. If the `outputElement` variable is not updated then `null` is inserted instead.

If the `inputCollection` value is empty then an empty array is propagated as `outputCollection`.

## Boundary Events

![multi-instance with boundary event](/bpmn-workflows/multi-instance/multi-instance-boundary-event.png)

Interrupting and non-interrupting boundary events can be attached to a multi-instance activity.

When an interrupting boundary event is triggered then the multi-instance body and **all active instances** are terminated. The `outputCollection` variable is not propagated to the parent scope (i.e. no partial output).

When an non-interrupting boundary event is triggered then the instances are not affected. The activities at the outgoing path have no access to the local variables since they are bounded to the multi-instance activity.

## Special Multi-Instance Variables

Every instance has a local variable `loopCounter`. It holds the index in the `inputCollection` of this instance, starting with `1`.

## Variable Mappings

Input and output variable mappings can be defined at the multi-instance activity. They are applied **on each instance** on activating and on completing.

The input mappings can be used to create new local variables in the scope of an instance. These variables are only visible within the instance. It is a way to restrict the **visibility** of variables. By default, new variables (e.g. provided by a job worker) are created in the scope of the workflow instance and are visible to all instances of the multi-instance activity as well as outside of it. In case of a parallel multi-instance activity, this can lead to variables that are modified by multiple instances and result in race conditions. If a variable is defined as local variable, then it is not propagated to a parent or the workflow instance scope and can't be modified outside of the instance.

The input mappings can access the local variables of the instance (e.g. `inputElement`, `loopCounter`). For example, to extract parts of the `inputElement` variable and apply them to separate variables.

The output mappings can be used to update the `outputElement` variable. For example, to extract a part of the job variables.

**Example:** say we have a call activity that is marked as a parallel multi-instance. When the
called workflow instance completes, its variables get [merged](/reference/variables.html#variable-propagation)
into the call activity's workflow instance. Its result is collected in the output collection
variable, but this has become a race condition where each completed child instance again overwrites
this same variable. We end up with a corrupted output collection. An output mapping can used to
overcome this, because it restricts which variables get merged. In the case that:

- parallel multi-instance call activity
- multi-instance output element: `=output`
- variable in the child instance that holds the result: `x`

The output mapping on the call activity should then be:

```
source: =x
target: output
```

## Additional Resources

<details>
  <summary>XML representation</summary>
  <p>A sequential multi-instance service task:

```xml
<bpmn:serviceTask id="task-A" name="A">
  <bpmn:multiInstanceLoopCharacteristics>
    <bpmn:extensionElements>
      <zeebe:loopCharacteristics isSequential="true"
          inputCollection="= items" inputElement="item"
          outputCollection="results" outputElement="= result" />
    </bpmn:extensionElements>
  </bpmn:multiInstanceLoopCharacteristics>
</bpmn:serviceTask>
```

  </p>
</details>

<details>
  <summary>Using the BPMN modeler</summary>
  <p>Adding the parallel multi-instance marker to a service task:

![multi-instance](/bpmn-workflows/multi-instance/bpmn-modeler-multi-instance.gif)

  </p>
</details>

<details>
  <summary>Workflow Lifecycle</summary>
  <p>Workflow instance records of a parallel multi-instance service task:

<table>
    <tr>
        <th>Intent</th>
        <th>Element Id</th>
        <th>Element Type</th>
    </tr>
    <tr>
        <td>ELEMENT_ACTIVATING</td>
        <td>task-a</td>
        <td>MULTI_INSTANCE_BODY</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATED</td>
        <td>task-a</td>
        <td>MULTI_INSTANCE_BODY</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATING</td>
        <td>task-a</td>
        <td>SERVICE_TASK</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATING</td>
        <td>task-a</td>
        <td>SERVICE_TASK</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATED</td>
        <td>task-a</td>
        <td>SERVICE_TASK</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATED</td>
        <td>task-a</td>
        <td>SERVICE_TASK</td>
    <tr>
    <tr>
        <td>...</td>
        <td>...</td>
        <td>...</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETED</td>
        <td>task-a</td>
        <td>SERVICE_TASK</td>
    <tr>
    <tr>
        <td>...</td>
        <td>...</td>
        <td>...</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETED</td>
        <td>task-a</td>
        <td>SERVICE_TASK</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETING</td>
        <td>task-a</td>
        <td>MULTI_INSTANCE_BODY</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETED</td>
        <td>task-a</td>
        <td>MULTI_INSTANCE_BODY</td>
    <tr>
</table>

  </p>
</details>

References:
* [Variable Scopes](/reference/variables.html#variable-scopes)
* [Expressions](/reference/expressions.html)
* [Variable Mappings](/reference/variables.html#inputoutput-variable-mappings)
