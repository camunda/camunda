# Expressions

Expressions can be used to access variables and calculate values dynamically.

The following attributes of BPMN elements **require** an expression:
* Sequence Flow on an Exclusive Gateway: [condition](/bpmn-workflows/exclusive-gateways/exclusive-gateways.html#conditions)
* Message Catch Event / Receive Task: [correlation key](/bpmn-workflows/message-events/message-events.html#messages)
* Multi-Instance Activity: [input collection](/bpmn-workflows/multi-instance/multi-instance.html#defining-the-collection-to-iterate-over), [output element](/bpmn-workflows/multi-instance/multi-instance.html#collecting-the-output)
* Input/Output Variable Mappings: [source](/reference/variables.html#inputoutput-variable-mappings)

Additionally, the following attributes of BPMN elements can define an expression **optionally** instead of a static value:
* Timer Catch Event: [timer definition](/bpmn-workflows/timer-events/timer-events.html#timers)
* Service Task: [job type](/bpmn-workflows/service-tasks/service-tasks.html#task-definition), [job retries](/bpmn-workflows/service-tasks/service-tasks.html#task-definition)
* Call Activity: [process id](/bpmn-workflows/call-activities/call-activities.html#defining-the-called-workflow)

## Expressions vs. Static Values

Some attributes of BPMN elements, like the timer definition of a timer catch event, can be defined either as a static value (e.g. `PT2H`) or as an expression (e.g. `= remaingTime`).

The value is identified as an expression if it starts with an **equal sign** `=` (i.e. the expression prefix). The text behind the equal sign is the actual expression. For example, `= remaingTime` defines the expression `remaingTime` that accesses a variable with the name `remaingTime`.

If the value doesn't have the prefix then it is used as static value. A static value is used either as a string (e.g. job type) or as a number (e.g. job retries). A string value must not be enclosed in quotes.

Note that an expression can also define a static value by using literals (e.g. `"foo"`, `21`, `true`, `[1,2,3]`, `{x: 22}`, etc.).

## The Expression Language

An expression is written in **FEEL** (Friendly Enough Expression Language). FEEL is part of the OMG's DMN (Decision Model and Notation) specification. It is designed to have the following properties:

* Side-effect free
* Simple data model with JSON-like object types: numbers, dates, strings, lists, and contexts
* Simple syntax designed for business professionals and developers
* Three-valued logic (true, false, null)

Zeebe integrates the [Feel-Scala](https://github.com/camunda/feel-scala) engine to evaluate FEEL expressions. The following sections cover common use cases in Zeebe. A complete list of supported expressions can be found in the project's [documentation](https://camunda.github.io/feel-scala).

### Access Variables

A variable can be accessed by its name.

```feel
owner
// "Paul"

totalPrice
// 21.2

items
// ["item-1", "item-2", "item-3"]
```

If a variable is a JSON document/object then it is handled as a FEEL context. A property of the context (aka nested variable property) can be accessed by `.` (a dot) and the property name.

```feel
order.id
// "order-123"

order.customer.name
// "Paul"
```

### Boolean Expressions

Values can be compared using the following operators:

<table style="width:100%">
  <tr>
    <th>Operator</th>
    <th>Description</th>
    <th>Example</th>
  </tr>

  <tr>
    <td>= (only <b>one</b> equal sign)</td>
    <td>equal to</td>
    <td>owner = "Paul"</td>
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

   <tr>
    <td>between _ and _</td>
    <td>same as <i>(x &#62;= _ and x &#60;= _)</i></td>
    <td>totalPrice between 10 and 25</td>
   </tr>

</table>

Multiple boolean values can be combined as disjunction (`and`) or conjunction (`or`).

```feel
orderCount >= 5 and orderCount < 15

orderCount > 15 or totalPrice > 50
```

If a variable or a nested property can be `null` then it can be compared to the `null` value. Comparing `null` to a value different from `null` results in `false`.

```feel
order = null
// true if order is null

totalCount > 5
// false is totalCount is null
```

### String Expressions

A string value must be enclosed in double quotes. Multiple string values can be concatenated using the `+` operator.

```feel
"foo" + "bar"
// "foobar"
```

Any value can be transformed into a string value using the `string()` function.

```feel
"order-" + string(orderId)
// "order-123"
```

More functions for string values are available as [built-in functions](https://camunda.github.io/feel-scala/feel-built-in-functions#string-functions) (e.g. contains, matches, etc.).

### Temporal Expressions

The following operators can be applied on temporal values:

<table style="width:100%">
  <tr>
    <th>Temporal Type</th>
    <th>Examples</th>
    <th>Operators</th>
  </tr>

  <tr>
    <td>date</td>
    <td>date("2020-04-06")</td>
    <td>
      <li>date + duration
      <li>date - date
      <li>date - duration
    </td>
  </tr>

  <tr>
    <td>time</td>
    <td>
      time("15:30:00"),<br>
      time("15:30:00+02:00"),<br>
      time("15:30:00@Europe/Berlin")
    </td>
    <td>
      <li>time + duration
      <li>time - time
      <li>time - duration
    </td>
  </tr>

  <tr>
    <td>date-time</td>
    <td>
      date and time("2020-04-06T15:30:00"),<br>
      date and time("2020-04-06T15:30:00+02:00"),<br>
      date and time("2020-04-06T15:30:00@UTC")
    </td>
    <td>
      <li>date-time + duration
      <li>date-time - date-time
      <li>date-time - duration
    </td>
  </tr>

  <tr>
    <td>duration</td>
    <td>duration("P12H"),<br> duration("P4Y")</td>
    <td>
      <li>duration + duration
      <li>duration + date
      <li>duration + time
      <li>duration + date-time
      <li>duration - duration
      <li>date - duration
      <li>time - duration
      <li>date-time - duration
      <li>duration * number
      <li>duration / duration
      <li>duration / number
    </td>
  </tr>

  <tr>
    <td>cycle</td>
    <td>cycle(3, duration("PT1H")),<br> cycle(duration("P7D"))</td>
    <td> </td>
  </tr>

</table>

A temporal value can be compared in a boolean expression with another temporal value of the same type.

The `cycle` type is different from the other temporal types because it is not supported in the FEEL type system. Instead, it is defined as a function that returns the definition of the cycle as a string in the ISO 8601 format of a recurring time interval. The function expects two arguments: the number of repetitions and the recurring interval as duration. If the first argument is `null` or not passed in then the interval is unbounded (i.e. infinitely repeated).

```feel
cycle(3, duration("PT1H"))
// "R3/PT1H"

cycle(duration("P7D"))
// "R/P7D"
```

### List Expressions

An element of a list can be accessed by its index. The index starts at `1` with the first element (**not** at `0`). A negative index starts at the end by `-1`. If the index is out of the range of the list then `null` is returned instead.

```feel
["a","b","c"][1]
// "a"

["a","b","c"][2]
// "b"

["a","b","c"][-1]
// "c"
```

A list value can be filtered using a boolean expression. The result is a list of elements that fulfill the condition. The current element in the condition is assigned to the variable `item`.

```feel
[1,2,3,4][item > 2]
// [3,4]
```

The operators `every` and `some` can be used to test if all elements or at least one element of a list fulfill a given condition.

```feel
every x in [1,2,3] satisfies x >= 2
// false

some x in [1,2,3] satisfies x > 2
// true
```

### Invoke Functions

FEEL defines a set of [built-in functions](https://camunda.github.io/feel-scala/feel-built-in-functions) to convert values and to apply different operations on specific value types in addition to the operators.

A function can be invoked by its name followed by the arguments. The arguments can be assigned to the function parameters either by their position or by defining the parameter names.

```feel
floor(1.5)
// 1

count(["a","b","c"])
// 3

append(["a","b"], "c")
// ["a","b","c"]

contains(string: "foobar", match: "foo")
// true
```

## Additional Resources

References:
* [FEEL-Scala - Documentation](https://camunda.github.io/feel-scala)
* [FEEL - Data Types](https://camunda.github.io/feel-scala/feel-data-types)
* [FEEL - Expressions](https://camunda.github.io/feel-scala/feel-expression)
* [FEEL - Built-in Functions](https://camunda.github.io/feel-scala/feel-built-in-functions)
* [DMN Specification](https://www.omg.org/spec/DMN/About-DMN/)
