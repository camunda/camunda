# Conditions

Conditions can be used for conditional flows to determine the following task.

A condition is a Boolean expression with a JavaScript-like syntax. It allows to compare variables of a workflow instance with other variables or literals (e.g., numbers, strings, etc.).

Variables of a workflow instance are accessed by its name. If a variable has a document value then the nested properties can be accessed via dot notation. See the [Variables](reference/variables.html#access-variables) section for details.

Examples:

```
totalPrice > 100

owner == "Paul"

order.count >= 5 && order.count < 15
```

### Literals

<table style="width:100%">
  <tr>
    <th>Literal</th>
    <th>Examples</th
  </tr>

  <tr>
    <td>Variable</td>
    <td>totalPrice, order.id</td>
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

A Null-Value can be used to check if a variable or nested property is set (e.g., `owner == null`).
If a variable or nested property doesn't exist, then it is resolved to `null` and can be compared as such.

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
    <td>owner == "Paul"</td>
  </tr>

  <tr>
    <td>!=</td>
    <td>not equal to</td>
    <td>owner != "Paul"</td>
  </tr>

  <tr>
    <td>&#60;</td>
    <td>less than</td>
    <td>totalPrice &#60; 25</td>
  </tr>

  <tr>
    <td>&#60;=</td>
    <td>less than or equal to</td>
    <td>totalPrice &#60;= 25</td>
  </tr>

  <tr>
    <td>&#62;</td>
    <td>greater than</td>
    <td>totalPrice &#62; 25</td>
  </tr>

  <tr>
    <td>&#62;=</td>
    <td>greater than or equal to</td>
    <td>totalPrice &#62;= 25</td>
  </tr>
</table>

The operators `<`, `<=`, `>` and `>=` can only be used for numbers.

If the values of an operator have different types, then the evaluation fails.
Comparing null or missing property with a number is considered as comparing different types.

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
    <td>orderCount &#62;= 5 && orderCount &#60; 15</td>
  </tr>

  <tr>
    <td>||</td>
    <td>or</td>
    <td>orderCount &#62; 15 || totalPrice &#62; 50</td>
  </tr>
</table>

It's also possible to use parentheses between the operators to change the precedence (e.g., `(owner == "Paul" || owner == "Jonny") && totalPrice > 25`).
