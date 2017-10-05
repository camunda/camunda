# JSON

Zeebe uses JSON for:

* User-provided payload
* Internal data
* API and protocol messages



## Message Pack

For performance reasons JSON is represented using MessagePack, which is a binary representation of JSON. Using MessagePack allows the broker to traverse a JSON document on the binary level without interpreting it as a string and without needing to "parse" it.

As a user, you do not need to deal with MessagePack directly. The clients take care of converting between MessagePack and JSON.

## Conditions

Conditions can be used for exclusive gateways (i.e., conditional flows) to determine the following task.  

A condition is a boolean expression with a JavaScript-like syntax.
It allows to compare properties of the workflow instance payload with other properties or literals (e.g., numbers, strings, etc.).
The payload properties are selected using JSON Path.

```
$.totalPrice > 100

$.owner == "Paul"

$.orderCount >= 5 && $.orderCount < 15
```

### Literals

<table style="width:100%">
  <tr>
    <th>Literal</th>
    <th>Examples</th
  </tr>

  <tr>
    <td>JSON Path</td>
    <td>$.totalPrice, $.order.id, $.items[0].name</td>
  </tr>

  <tr>
    <td>Number</td>
    <td>25, 4.5, -3, -5.5</td>
  </tr>

  <tr>
    <td>String</td>
    <td>"Paul", 'Jonny'</td>
  </tr>

  <tr>
    <td>Boolean</td>
    <td>true, false</td>
  </tr>

  <tr>
    <td>Null-Value</td>
    <td>null</td>
  </tr>
</table>

A Null-Value can be used to check if a property is set (e.g., `$.owner == null`).
If the property doesn't exist, then the JSON Path evaluation fails.

### Comparison Operators

<table style="width:100%">
  <tr>
    <th>Operator</th>  
    <th>Description</th>
    <th>Example</th
  </tr>

  <tr>
    <td>==</td>
    <td>equal to</td>
    <td>$.owner == "Paul"</td>
  </tr>

  <tr>
    <td>!=</td>
    <td>not equal to</td>
    <td>$.owner != "Paul"</td>
  </tr>

  <tr>
    <td>&#60;</td>
    <td>less than</td>
    <td>$.totalPrice &#60; 25</td>
  </tr>

  <tr>
    <td>&#60;=</td>
    <td>less than or equal to</td>
    <td>$.totalPrice &#60;= 25</td>
  </tr>

  <tr>
    <td>&#62;</td>
    <td>greater than</td>
    <td>$.totalPrice &#62; 25</td>
  </tr>

  <tr>
    <td>&#62;=</td>
    <td>greater than or equal to</td>
    <td>$.totalPrice &#62;= 25</td>
  </tr>
</table>

The operators `<`, `<=`, `>` and `>=` can only be used for numbers.

If the values of an operator have different types, then the evaluation fails.

### Logical Operators

<table style="width:100%">
  <tr>
    <th>Operator</th>  
    <th>Description</th>
    <th>Example</th
  </tr>

  <tr>
    <td>&&</td>
    <td>and</td>
    <td>$.orderCount &#62;= 5 && $.orderCount &#60; 15</td>
  </tr>

  <tr>
    <td>||</td>
    <td>or</td>
    <td>$.orderCount &#62; 15 || $.totalPrice &#62; 50</td>
  </tr>
</table>

It's also possible to use parentheses between the operators to change the precedence (e.g., `($.owner == "Paul" || $.owner == "Jonny") && $.totalPrice > 25`).
