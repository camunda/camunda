# Data Flow

On creation of a workflow instance an corresponding workflow payload is created, which can contain variable data.
The workflow payload is represented as a JSON document. On execution of the workflow instance
the workflow payload is passed from one activity to another, this is called data flow.

It is possible that a task handler expect an other JSON document structure as the task handler before or as the
current structure of the workflow payload is. In this cases mappings can be used to modify the current JSON document.
We differentiate between input and output mappings.
They are defined as extension elements on the corresponding task definition, see the following example.

## Mapping

Lets see for example a clothing web shop and the following workflow defines there ordering process.

![order-process](/bpmn-workflows/order-process.png)

The first task orders the articles from the logistic center, the second debit the total price from the customers bank account.
The third task will simply initiate the shipping of the articles to the customer.

For example a customer ordered tree black socks and a jeans.
After the order is send by the customer a workflow instance is created, which contain
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
But a single task is not really interessted in all the data and can also expected a different structure of the payload.

For example the task to debit the total price from the customers bank account is only interessted in the total price of the order and the order id.
So the JSON structure of the task payload, which is expected by the second task handler, would look like this:

```json
{
 "orderId": 43516423,
 "sum": 91.96
}
```

To extract the `orderId` and the `totalPrice` and rename it to `sum` the following mapping can be used.

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

As you can see, also an output mapping is defined for the debit task, since the task is additionally willing to give feedback
if the debit was successful. In that case we need the output mapping to map the result to the workflow instance payload.

The debit task needs to be completed with a new task payload, which contains the property `wasDebit`.
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

Regarding to the result of the second task the third task can operate different.
For example if the `successfulDebit` is false he can fail the execution. In the following section
input and output mappings are explained in more detail.

### Input and Output Mapping

Input mappings are executed on task creation. Input mappings can extract payload of the
workflow instance and map it into the task payload. Per default, if no input mapping is defined, the complete workflow payload is mapped
into the task payload.

Output mappings are executed on task completion. With output mappings it is possible to merge
the task payload with the existing workflow instance payload. Per default, if no output mapping exist
and the task was completed without payload, the workflow uses the same payload, which has used
before the task creation. In that case, no changes on the payload happens.

If the task was completed with a payload and no output mapping was defined the task payload will replace the workflow instance
payload.

It is possible to define multiple input and output mappings as you can see in the example above.
These mappings are executed in the defined order, this means a second mapping can overwrite the first one.

There exists different cases regarding to defined input, output mapping and whether the task was completed
with or without payload. Theses cases are summarized in the following table.

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


Since the payload is represented as JSON document the mappings are expressed as [JSON Path expressions](http://goessner.net/articles/JsonPath/).
A mapping consist of a `source` and `target`. The `source` matches a JSON property
in the source JSON document and the `target` defines the path in the target JSON document,
on which the value of the matching source should be written to. In the following section we dscribe shortly the json path expressions.

#### JSON Path expression

JSON Path expressions always refer to a JSON structure in the same way as XPath expression are used in combination with an XML document.
Since a JSON structure is usually anonymous and doesn't necessarily have a "root member object"
JSON Path assumes the abstract name `$` assigned to the outer level object. [1](http://goessner.net/articles/JsonPath/)

The payload will be on root level always a JSON Object.
JSON Path expressions can use the dot or bracket notation.
For example `$.prop` or `$['prop']` will match in the following JSON document and return the value `123`.

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

To match the `innerProp` we have to use one of the following expression `$.object.innerProp` or `$['object']['innerProp']`.

Values in JSON Arrays can be accessed via square brackets and the corresponding index. Say we have the following expression `$.array[2]`
or `$['array'][2]`.
This will match and return the value `5`, if we have for example the following JSON document.

```json
{
 "array" : [4, 3, 5]
}
```

**Note:**
In the following examples we will prefer the dot notation, since it is more readable.

The following table contains the JSON Path features/syntax, which is supported by Zeebe.

<table>
<tr>
  <th>JSONPath</th> <th>Description</th> <th>Supported</th>
</tr>

<tr>
  <td>$</td> <td>the root object/element</td> <td>Yes</td>
</tr>

<tr>
  <td>@</td> <td>the current object/element</td> <td>No</td>
</tr>

<tr>
  <td>. or []</td> <td>child operator</td> <td>Yes</td>
</tr>

<tr>
  <td>..</td> <td>recursive descent</td> <td>No</td>
</tr>

<tr>
  <td>*</td> <td>wildcard. All objects/elements regardless their names.</td><td>Yes</td>
</tr>

<tr>
  <td>[]</td> <td>subscript operator</td> <td>Yes</td>
</tr>

<tr>
  <td>[,]</td> <td>union operator</td> <td>No</td>
</tr>

<tr>
  <td>[start:end:step]</td> <td>array slice operator</td> <td>No</td>
</tr>

<tr>
  <td>?()</td> <td>applies a filter (script) expression</td> <td>No</td>
</tr>

<tr>
  <td>()</td> <td>script expression, using underlying script engine</td> <td>No</td>
</tr>

</table>

#### Input Mapping Example

Input mappings extract content and copy it to an empty target payload.

In the following table some input mappings examples are listed, to give a overview of what is possible with the input mappings.
The column workflow instance payload contains the outgoing payload, the input mapping the mapping which is executed to get
the result which is listed in the task payload column. Each line has a description to clarify for what the
mapping can be used.

<table>

  <tr>
    <th>Workflow Instance Payload</th>
    <th>Input Mapping</th>
    <th>Task Payload</th>
    <th>Description</th>
  </tr>

  <tr>
    <td><pre>
{
 "price": 342.99,
 "productId": "3232-24-241"
}
    </pre></td>
    <td><pre>
Source: $
Target: $
    </pre></td>
    <td><pre>
{
 "price": 342.99,
 "productId": "3232-24-241"
}
    </pre></td>
    <td>
    Copy hole payload
    </td>
  </tr>

  <tr>
    <td><pre>
{
 "price": 342.99,
 "productId": "3232-24-241"
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
    "productId": "3232-24-241"
  }
}
    </pre></td>
    <td>
    Move payload in new object
    </td>
  </tr>

  <tr>
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
    <td>
    Extract object
    </td>
  </tr>

  <tr>
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
    <td>
    Extract and put into new object
    </td>
  </tr>

  <tr>
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
Target: $.newCustomer.details
    </pre></td>
    <td><pre>
{
 "newCustomer":{
   "details": {
     "name": "Hans Horst",
     "customerId": 231
  }
 }
}
    </pre></td>
    <td>
    Extract and put into new objects
    </td>
  </tr>

  <tr>
    <td><pre>
{
 "name": "Hans Hols",
 "telephonNumbers": [
   "221-3231-31",
   "312-312313",
   "31-21313-1313"
  ],
  "age": 43
{
    </pre></td>
    <td><pre>
Source: $.telephoneNumbers
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
    <td>
    Extract array and put into new array
    </td>
  </tr>

  <tr>
    <td><pre>
{
 "name": "Hans Hols",
 "telephonNumbers": [
   "221-3231-31",
   "312-312313",
   "31-21313-1313"
  ],
  "age": 43
{
    </pre></td>
    <td><pre>
Source: $.telephoneNumbers[1]
Target: $.contactNrs[0]
    </pre></td>
    <td><pre>
{
 "contactNrs": [ "312-312313" ]
 }
}
    </pre></td>
    <td>
    Extract single array value and put into new array
    </td>
  </tr>

  <tr>
    <td><pre>
{
 "name": "Hans Hols",
 "telephonNumbers": [
   "221-3231-31",
   "312-312313",
   "31-21313-1313"
  ],
  "age": 43
{
    </pre></td>
    <td><pre>
Source: $.telephoneNumbers[1]
Target: $.contactNr
    </pre></td>
    <td><pre>
{
 "contactNr": "312-312313"
 }
}
    </pre></td>
    <td>
    Extract single array value and put into property
    </td>
  </tr>

</table>

For more examples see the [extract mapping tests](https://github.com/zeebe-io/zb-msgpack-json-path/blob/0.1.0/json-path/src/test/java/io/zeebe/msgpack/mapping/MappingExtractParameterizedTest.java).

#### Output Mapping Example

Output mappings extract content of a task payload and merge them with the workflow instance payload.

In the following table some output mappings examples are listed, to give a overview of what is possible with output mappings.
The column task payload refer to the task payload with the task was completed. The workflow instance payload column contains
the payload into which should be merged and the output mapping the mapping which will be executed to get the result which is listed
in the result column. Each row has a description to identify the use case.

<table>

  <tr>
    <th>Task payload</th>
    <th>Workflow instance Payload</th>
    <th>Output mapping</th>
    <th>Result</th>
    <th>Description</th>
  </tr>

<!-- NEW ROW -->
  <tr>
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
  <td>Replace with hole payload</td>
  </tr>

<!-- NEW ROW -->
  <tr>
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
  <td>Merge payload and write into new object</td>
  </tr>

<!-- NEW ROW -->
<tr>
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
  <td>Replace payload with object value</td>
</tr>


<!-- NEW ROW -->
<tr>
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
  <td>Merge payload and write into new property</td>
</tr>


<!-- NEW ROW -->


<tr>
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
  <td>Merge payload and write into array</td>
</tr>


<tr>
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
  <td>Merge and update array value</td>
</tr>

<tr>
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
  <td>Extract array value and write into payload</td>
</tr>

</table>

For more examples see the [merge mapping tests](https://github.com/zeebe-io/zb-msgpack-json-path/blob/0.1.0/json-path/src/test/java/io/zeebe/msgpack/mapping/MappingMergeParameterizedTest.java).
