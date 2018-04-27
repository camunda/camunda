# JSONPath

JSONPath is an expression language to extract data from a JSON document. It solves similar use cases that XPath solves for XML. See the [JSONPath documentation](http://goessner.net/articles/JsonPath/) for general information on the language.

## JSONPath Support in Zeebe

The following table contains the JSONPath features/syntax which are supported by Zeebe:

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