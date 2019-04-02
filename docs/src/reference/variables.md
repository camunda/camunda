# Variables

Variables are part of a workflow instance and represent the data of the instance. A variable has a name and a JSON value. The visibility of a variable is defined by its variable scope.

## Variable Values

The value of a variable is stored as a JSON value. It must have one of the following types:

* String
* Number
* Boolean
* Array
* Document/Object
* Null

## Access Variables

Variables can be accessed within the workflow instance, for example, on input/output mappings or conditions. In the expression, the variable is accessed by its name. If the variable is a document then the nested properties can be accessed via dot notation.

Examples:

<table>
  <tr>
    <th>Variables</th>
    <th>Expression</th>
    <th>Value</th>
  </tr>  
  <tr>
    <td><pre>
totalPrice: 25.0</pre></td>
    <td><pre>totalPrice</pre></td>
    <td><pre>25.0</pre></td>
  </tr>
  <tr>
    <td><pre>
order: {"id": "order-123",
  "items": ["item-1", "item-2"]}</pre></td>
    <td><pre>order</pre></td>
    <td><pre>
{"id": "order-123",
  "items": ["item-1", "item-2"]}</pre></td>
  </tr>
  <tr>
    <td><pre>
order: {"id": "order-123"}</pre></td>
    <td><pre>order.id</pre></td>
    <td><pre>"order-123"</pre></td>
  </tr>
  <tr>
    <td><pre>
order: {"items": ["item-1", "item-2"]}</pre></td>
    <td><pre>order.items</pre></td>
    <td><pre>["item-1", "item-2"]</pre></td>
  </tr>
</table>

## Variable Scopes

Variable scopes define the visibility of variables. The root scope is the workflow instance itself. Variables in this scope are visible everywhere in the workflow.

When the workflow instance enters a sub process or an activity then a new scope is created. Activities in this scope can see all variables of this and of higher scopes (i.e. parent scopes). But activities outside of this scope can not see the variables which are defined in this scopes.  

If a variable has the same name as a variable from a higher scope then it covers this variable. Activities in this scope see only the value of this variable and not the one from the higher scope.

The scope of a variable is defined when the variable is created. By default, variables are created in the root scope.

Example:

![variable-scopes](/reference/variable-scopes.png)

This workflow instance has the following variables:

* `a` and `b` are defined on the root scope and can be seen by *Task A*, *Task B*, and *Task C*.
* `c` is defined in the sub process scope and can be seen by *Task A* and *Task B*.
* `b` is defined again on the activity scope of *Task A* and can be seen only by *Task A*. It covers the variable `b` from the root scope.

### Variable Propagation

When variables are merged into a workflow instance (e.g. on job completion, on message correlation) then each variable is propagated from the scope of the activity to its higher scopes.

The propagation ends when a scope contains a variable with the same name. In this case, the variable value is updated.

If no scope contains this variable then it is created as a new variable in the root scope.

Example:

![variable-propagation](/reference/variable-propagation.png)

The job of *Task B* is completed with the variables `b`, `c`, and `d`. The variables `b` and `c` are already defined in higher scopes and are updated with the new values. Variable `d` doesn't exist before and is created in the root scope.

### Local Variables

In some cases, variables should be set in a given scope, even if they don't exist in this scope before.

In order to deactivate the variable propagation, the variables are set as *local variables*. That means that the variables are created or updated in the given scope, whether they exist in this scope before or not.

## Input/Output Variable Mappings

Input/output variable mappings can be used to create new variables or customize how variables are merged into the workflow instance.

Variable mappings are defined in the workflow as extension elements under `ioMapping`. Every variable mapping has a `source` and a `target` expression. The *source* expression defines where the value is copied from. The *target* expression defines where the value is copied to. The expressions reference a variable by its name or a nested property of a variable.

If a variable or a nested property of a *target* expression doesn't exist then it is created. But if a variable or a nested property of a *source* expression doesn't exist then an [incident](reference/incidents.html) is created.

Example:

![variable-mappings](/reference/variable-mappings.png)

XML representation:

```XML
<serviceTask id="collectMoney" name="Collect Money">
    <extensionElements>
      <zeebe:ioMapping>
        <zeebe:input source="customer.name" target="sender"/>
        <zeebe:input source="customer.iban" target="iban"/>
        <zeebe:input source="totalPrice" target="price"/>
        <zeebe:input source="reference" target="orderId"/>
        <zeebe:output source="status" target="paymentStatus"/>
       </zeebe:ioMapping>
    </extensionElements>
</serviceTask>
```

### Input Mappings

Input mappings can used to create new variables. They can be defined on service tasks and sub processes.

When an input mapping is applied then it creates a new variable in the scope where the mapping is defined.

Examples:

<table>
  <tr>
    <th>Workflow Instance Variables</th>
    <th>Input Mappings</th>
    <th>New Variables</th>
  </tr>  
  <tr>
    <td><pre>
orderId: "order-123"</pre></td>
    <td><pre>
source: orderId
target: reference</pre></td>
    <td><pre>
reference: "order-123"</pre></td>
  </tr>
  <tr>
    <td><pre>
customer: {"name": "John"}</pre></td>
    <td><pre>
source: customer.name
target: sender</pre></td>
    <td><pre>
sender: "John"</pre></td>
  </tr>
  <tr>
    <td><pre>
customer: "John"
iban: "DE456"</pre></td>
    <td><pre>
source: customer
target: sender.name

source: iban
target: sender.iban</pre></td>
    <td><pre>
sender: {"name": "John",
  "iban": "DE456"}</pre></td>
  </tr>
</table>

### Output Mappings

Output mappings can be used to customize how job/message variables are merged into the workflow instance. They can be defined on service tasks, receive tasks, message catch events and sub processes.

If one or more output mappings are defined then the job/message variables are set as *local variables* in the scope where the mapping is defined. Then, the output mappings are applied to the variables and create new variables in this scope. The new variables are merged into the parent scope. If there is no mapping for a job/message variable then the variable is not merged.  

If no output mappings are defined then all job/message variables are merged into the workflow instance.

In case of a sub process, the behavior is different. There are no job/message variables to be merged. But output mappings can be used to propagate *local variables* of the sub process to higher scopes. By default, all *local variables* are removed when the scope is left. 

Examples:

<table>
  <tr>
    <th>Job/Message Variables</th>
    <th>Output Mappings</th>
    <th>Workflow Instance Variables</th>
  </tr>  
  <tr>
    <td><pre>
status: "Ok"</pre></td>
    <td><pre>
source: status
target: paymentStatus</pre></td>
    <td><pre>
paymentStatus: "OK"</pre></td>
  </tr>
  <tr>
    <td><pre>
result: {"status": "Ok",
  "transactionId": "t-789"}</pre></td>
    <td><pre>
source: result.status
target: paymentStatus

source: result.transactionId
target: transactionId</pre></td>
    <td><pre>
paymentStatus: "Ok"
transactionId: "t-789"</pre></td>
  </tr>
  <tr>
  <td><pre>
status: "Ok"
transactionId: "t-789"</pre></td>
    <td><pre>
source: transactionId
target: order.transactionId</pre></td>
    <td><pre>
order: {"transactionId": "t-789"}</pre></td>
  </tr>
</table>
