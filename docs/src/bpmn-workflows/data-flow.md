# Data Flow

On creation of a workflow instance, a corresponding workflow payload is created, which can contain variable data.
The workflow payload is represented as a JSON document. On execution of the workflow instance, 
the workflow payload is passed from one activity to another. This is called data flow.

It is possible that a task handler expects a different JSON document structure than the task handler before or than the
current structure of the workflow payload. In these cases, mappings can be used to modify the current JSON document.
We differentiate between input and output mappings.
They are defined as extension elements on the corresponding task definition. See the following example.

## Mapping

For example, let's take a look at an online fashion retailer. The following workflow defines their ordering process:

![order-process](/bpmn-workflows/order-process.png)

The first task orders the articles from the logistics center, the second debits the total price from the customers bank account.
The third task simply initiates shipping of the articles to the customer.

For example, a customer orders three pairs of black socks and a pair of jeans.
After the order is sent by the customer, a workflow instance is created, which contains
the following workflow instance payload:

```java
{
  "orderId": 43516423,
  "totalPrice": 91.96,
  "isShipAddressEqualToInvoiceAddress": true,
  "shipAddress": null,
  "invoiceAddress": {
    "street": "Borrowway",
    "postcode": "SO40 9DA",
    "city": "Southampton",
    "country": "UK"
  },
  "order": [
    {
      "id": "BS-121-48274",
      "name": "black socks",
      "quantity": 3,
      "price": 3.99
     },
     {
      "id": "J-954-23365",
      "name": "jeans",
      "quantity": 1,
      "price": 79.99
     }
  ]
}
```

Now we are interesseted in the data flow. The workflow instance payload contains all necessary data for all tasks in the workflow.
However, a single task is not necesarrily interested in all the data and can also expect a different structure of the payload.

For example, the task to debit the total price from the customers bank account is only interested in the total price of the order and the order id.
So, the JSON structure of the task payload, which is expected by the second task handler, would look like this:

```json
{
 "orderId": 43516423,
 "sum": 91.96
}
```

To extract the `orderId` and the `totalPrice` and rename it to `sum`, the following JSON path based mapping can be used.

```xml
  <serviceTask id="debitTask">
      <extensionElements>
        <ioMapping>
          <input source="$.totalPrice" target="$.sum"/>
          <input source="$.orderId" target="$.orderId"/>
          <output source="$.wasDebit" target="$.successfulDebit"/>
         </ioMapping>
      </extensionElements>
  </serviceTask>
```

As you can see, an output mapping is also defined for the debit task, since the task is additionally willing to give feedback
if the debit was successful. In that case, we need the output mapping to map the result to the workflow instance payload.

The debit task needs to be completed with a new task payload which contains the property `wasDebit`.
This property indicates whether the debit was successful or not. The completing task payload would look like this:

```json
{
 "wasDebit": true
}
```

The task payload is merged with the help of the defined output mapping into the existing workflow instance payload.
The updated workflow instance payload would look like this:

```java
{
  "orderId": 43516423,
  "totalPrice": 91.96,
  "isShipAddressEqualToInvoiceAddress": true,
  "shipAddress": null,
  "invoiceAddress": {
    "street": "Borrowway",
    "postcode": "SO40 9DA",
    "city": "Southampton",
    "country": "UK"
  },
  "order": [
    {
      "id": "BS-121-48274",
      "name": "black socks",
      "quantity": 3,
      "price": 3.99
     },
     {
      "id": "J-954-23365",
      "name": "jeans",
      "quantity": 1,
      "price": 79.99
     }
  ],
  "successfulDebit": true
}
```

Depending on the result of the second task, the third task can operate in different ways.
For example, if the `successfulDebit` is `false`, it can fail the execution. 
In the following section input and output mappings are explained in more detail.

### Input and Output Mapping

Input mappings are executed on task creation. Input mappings can extract the payload of the
workflow instance and map it to the task payload. By default, if no input mapping is defined, the entire 
workflow payload is mapped to the task payload.

Output mappings are executed on task completion. With output mappings it is possible to merge
the task payload with the existing workflow instance payload. By default, if no output mappings exist
and the task was completed without a payload, the workflow uses the same payload which was used
before task creation. In that case, no changes on the payload occur.

It is possible to define multiple input and output mappings as you can see in the example above.
These mappings are executed in the defined order. This means that a second mapping can overwrite the first one.

There are different cases regarding the defined input and output mapping and whether the task was completed
with or without a payload. These cases are summarized in the following table:

<table>
<tr>
  <th>Input Mapping</th>  <th>Output Mapping</th><th>Complete with Payload</th><th>Result WF Payload</th>
</tr>

<tr>
  <td>Yes</td><td>Yes</td><td>Yes</td><td>Payloads are merged</td>
</tr>

<tr>
  <td>Yes</td><td>Yes</td><td>No</td><td>Incident!</td>
</tr>

<tr>
  <td>Yes</td><td>No</td><td>No</td><td>Initial WF payload before task</td>
</tr>

<tr>
  <td>No</td><td>No</td><td>No</td><td>Initial WF payload before task</td>
</tr>

<tr>
  <td>No</td><td>Yes</td><td>No</td><td>Incident!</td>
</tr>

<tr>
  <td>No</td><td>No</td><td>Yes</td><td>Completed Task payload</td>
</tr>

<tr>
  <td>No</td><td>Yes</td><td>Yes</td><td>Payloads are merged</td>
</tr>
<tr>
  <td>Yes</td><td>No</td><td>Yes</td><td>Completed Task payload</td>
</tr>
</table>


Since the payload is represented as a JSON document, the mappings are expressed as [JSON Path expressions](http://goessner.net/articles/JsonPath/).
A mapping consists of a `source` and a `target`. The `source` matches a JSON property
in the source JSON document and the `target` defines the path in the target JSON document onto which the value of the matching 
source should be written. In the following section we briefly describe the JSON path expressions.

#### JSON Path expression

JSON Path expressions always refer to a JSON structure in the same way as XPath expression are used in combination with an XML document.
JSON Path defines `$` as root. See the [JSON path documentation](http://goessner.net/articles/JsonPath/) for more information.

On the root level, the payload will always be a JSON Object.
JSON Path expressions can use the dot or bracket notation.
For example, `$.prop` or `$['prop']` will match in the following JSON document and return the value `123`.

```json
{
 "prop" : 123,
 "object":
 {
  "innerProp": 22
 },
 "other" : 256
}
```

To match the `innerProp`, we have to use one of the following expressions: `$.object.innerProp` or `$['object']['innerProp']`.

Values in JSON Arrays can be accessed via square brackets and the corresponding index. Let;s say we have the following expression: `$.array[2]`
or `$['array'][2]`.
For example, if we have the following JSON document, this will match and return the value `5`.

```json
{
 "array" : [4, 3, 5]
}
```

**Note:**
In the following examples we prefer the dot notation, as it is more readable.

The following table contains the JSON Path features/syntax which are supported by Zeebe:

<table>
<tr>
  <th>JSONPath</th> <th>Description</th> <th>Supported</th>
</tr>

<tr>
  <td>$</td> <td>The root object/element</td> <td>Yes</td>
</tr>

<tr>
  <td>@</td> <td>The current object/element</td> <td>No</td>
</tr>

<tr>
  <td>. or []</td> <td>Child operator</td> <td>Yes</td>
</tr>

<tr>
  <td>..</td> <td>Recursive descent</td> <td>No</td>
</tr>

<tr>
  <td>*</td> <td>Wildcard, matches all objects/elements regardless of their names.</td><td>Yes</td>
</tr>

<tr>
  <td>[]</td> <td>Subscript operator</td> <td>Yes</td>
</tr>

<tr>
  <td>[,]</td> <td>Union operator</td> <td>No</td>
</tr>

<tr>
  <td>[start:end:step]</td> <td>Array slice operator</td> <td>No</td>
</tr>

<tr>
  <td>?()</td> <td>Applies a filter (script) expression</td> <td>No</td>
</tr>

<tr>
  <td>()</td> <td>Script expression, using underlying script engine</td> <td>No</td>
</tr>

</table>

#### Input Mapping Example

Input mappings extract content and copy it to an empty target payload.

In the following table, some input mappings examples are listed to give a overview of what is possible with the input mappings.
The 'Workflow Instance Payload' column contains the outgoing payload, the 'Input Mapping' column contains the mapping which is executed to get
the result which is in turn listed in the 'Task Payload' column. Each line has a description to clarify what the mapping can be used for.

<table>

  <tr>
    <th>Description</th>
    <th>Workflow Instance Payload</th>
    <th>Input Mapping</th>
    <th>Task Payload</th>
  </tr>

  <tr>
    <td>
    Copy entire payload
    </td>
    <td><pre>
{
 "price": 342.99,
 "productId": 41234
}
    </pre></td>
    <td><pre>
Source: $
Target: $
    </pre></td>
    <td><pre>
{
 "price": 342.99,
 "productId": 41234
}
    </pre></td>
  </tr>

  <tr>
    <td>
    Move payload into new object
    </td>
    <td><pre>
{
 "price": 342.99,
 "productId": 41234
}
    </pre></td>
    <td><pre>
Source: $
Target: $.orderedItem
    </pre></td>
    <td><pre>
{
  "orderedItem": {
    "price": 342.99,
    "productId": 41234
  }
}
    </pre></td>
  </tr>

  <tr>
    <td>
    Extract object
    </td>
    <td><pre>
{
 "address": {
    "street": "Borrowway 1",
    "postcode": "SO40 9DA",
    "city": "Southampton",
    "country": "UK"
  },
 "name": "Hans Horn"
}
    </pre></td>
    <td><pre>
Source: $.address
Target: $
    </pre></td>
    <td><pre>
{
  "street": "Borrowway 1",
  "postcode": "SO40 9DA",
  "city": "Southampton",
  "country": "UK"
}
    </pre></td>
  </tr>

  <tr>
    <td>
    Extract and put into new object
    </td>
    <td><pre>
{
 "address": {
    "street": "Borrowway 1",
    "postcode": "SO40 9DA",
    "city": "Southampton",
    "country": "UK"
  },
 "name": "Hans Horn"
}
    </pre></td>
    <td><pre>
Source: $.address
Target: $.newAddress
    </pre></td>
    <td><pre>
{
 "newAddress":{
  "street": "Borrowway",
  "postcode": "SO40 9DA",
  "city": "Southampton",
  "country": "UK"
 }
}
    </pre></td>
  </tr>

  <tr>
    <td>
    Extract and put into new objects
    </td>
    <td><pre>
{
 "order":
 {
  "customer:{
   "name": "Hans Horst",
   "customerId": 231
  },
  "price": 34.99
 }
}
    </pre></td>
    <td><pre>
Source: $.order.customer
Target: $.new.details
    </pre></td>
    <td><pre>
{
 "new":{
   "details": {
     "name": "Hans Horst",
     "customerId": 231
  }
 }
}
    </pre></td>
  </tr>

  <tr>
    <td>
    Extract array and put into new array
    </td>
    <td><pre>
{
 "name": "Hans Hols",
 "numbers": [
   "221-3231-31",
   "312-312313",
   "31-21313-1313"
  ],
  "age": 43
{
    </pre></td>
    <td><pre>
Source: $.numbers
Target: $.contactNrs
    </pre></td>
    <td><pre>
{
 "contactNrs": [
   "221-3231-31",
   "312-312313",
   "31-21313-1313"
  ]
}
    </pre></td>
  </tr>

  <tr>
    <td>
    Extract single array value and put into new array
    </td>
    <td><pre>
{
 "name": "Hans Hols",
 "numbers": [
   "221-3231-31",
   "312-312313",
   "31-21313-1313"
  ],
  "age": 43
{
    </pre></td>
    <td><pre>
Source: $.numbers[1]
Target: $.contactNrs[0]
    </pre></td>
    <td><pre>
{
 "contactNrs": [
   "312-312313"
  ]
 }
}
    </pre></td>
  </tr>

  <tr>
    <td>
    Extract single array value and put into property
    </td>
    <td><pre>
{
 "name": "Hans Hols",
 "numbers": [
   "221-3231-31",
   "312-312313",
   "31-21313-1313"
  ],
  "age": 43
{
    </pre></td>
    <td><pre>
Source: $.numbers[1]
Target: $.contactNr
    </pre></td>
    <td><pre>
{
 "contactNr": "312-312313"
 }
}
    </pre></td>
  </tr>

</table>

For more examples see the [extract mapping tests](https://github.com/zeebe-io/zb-msgpack-json-path/blob/0.1.0/json-path/src/test/java/io/zeebe/msgpack/mapping/MappingExtractParameterizedTest.java).

#### Output Mapping Example

Output mappings extract content of a task payload and merges it with the workflow instance payload.

In the following table, some output mappings examples are listed to give an overview of what is possible with output mappings.
The 'Task Payload' column refers to the task payload when the task was completed. The 'Workflow Instance Payload' column contains
the payload into which the merge should take place and the 'Output Mapping' column contains the mapping which will be executed to get the 
result which is listed in the 'Result' column. Each row has a description to identify the use case.

<table>

  <tr>
    <th>Description</th>
    <th>Task Payload</th>
    <th>Workflow Instance Payload</th>
    <th>Output Mapping</th>
    <th>Result</th>
  </tr>

<!-- NEW ROW -->
  <tr>
  <td>Replace with entire payload</td>
  <td><pre>
{
 "sum": 234.97
}
  </pre></td>

  <td><pre>
{
 "prices": [
   199.99,
   29.99,
   4.99]
}
  </pre></td>

  <td><pre>
Source: $
Target: $
  </pre></td>

  <td><pre>
{
 "sum": 234.97
}
  </pre></td>
  </tr>

<!-- NEW ROW -->
  <tr>
  <td>Merge payload and write into new object</td>
  <td><pre>
{
 "sum": 234.97
}
  </pre></td>

  <td><pre>
{
 "prices": [
   199.99,
   29.99,
   4.99]
}
  </pre></td>

  <td><pre>
Source: $
Target: $.total
  </pre></td>

  <td><pre>
{
 "prices": [
   199.99,
   29.99,
   4.99],
 "total": {
  "sum": 234.97
 }
}
  </pre></td>
  </tr>

<!-- NEW ROW -->
<tr>
  <td>Replace payload with object value</td>
  <td><pre>
{
 "order":{
  "id": 12,
  "sum": 21.23
 }
}
  </pre></td>

  <td><pre>
{
 "ordering": true
}
  </pre></td>

  <td><pre>
Source: $.order
Target: $
  </pre></td>

  <td><pre>
{
  "id": 12,
  "sum": 21.23
}
  </pre></td>
</tr>


<!-- NEW ROW -->
<tr>
  <td>Merge payload and write into new property</td>
  <td><pre>
{
 "sum": 234.97
}
  </pre></td>

  <td><pre>
{
 "prices": [
   199.99,
   29.99,
   4.99]
}
  </pre></td>

  <td><pre>
Source: $.sum
Target: $.total
  </pre></td>

  <td><pre>
{
 "prices": [
   199.99,
   29.99,
   4.99],
 "total": 234.97
}
  </pre></td>
</tr>


<!-- NEW ROW -->

<tr>
  <td>Merge payload and write into array</td>
  <td><pre>
{
 "prices": [
   199.99,
   29.99,
   4.99]
}
  </pre></td>

  <td><pre>
{
 "orderId": 12
}
  </pre></td>

  <td><pre>
Source: $.prices
Target: $.prices
  </pre></td>

  <td><pre>
{
 "orderId": 12,
 "prices": [
   199.99,
   29.99,
   4.99]
}
  </pre></td>
</tr>


<tr>
  <td>Merge and update array value</td>
  <td><pre>
{
 "newPrices": [
   199.99,
   99.99,
   4.99]
}
  </pre></td>

  <td><pre>
{
 "prices": [
   199.99,
   29.99,
   4.99]
}
  </pre></td>

  <td><pre>
Source: $.newPrices[1]
Target: $.prices[0]
  </pre></td>

  <td><pre>
{
 "prices": [
   99.99,
   29.99,
   4.99]
}
  </pre></td>
</tr>

<tr>
  <td>Extract array value and write into payload</td>
  <td><pre>
{
 "newPrices": [
   199.99,
   99.99,
   4.99]
}
  </pre></td>

  <td><pre>
{
 "orderId": 12
}
  </pre></td>

  <td><pre>
Source: $.newPrices[1]
Target: $.price
  </pre></td>

  <td><pre>
{
 "orderId": 12,
 "price": 99.99
}
  </pre></td>
</tr>

</table>

For more examples see the [merge mapping tests](https://github.com/zeebe-io/zb-msgpack-json-path/blob/0.1.0/json-path/src/test/java/io/zeebe/msgpack/mapping/MappingMergeParameterizedTest.java).
