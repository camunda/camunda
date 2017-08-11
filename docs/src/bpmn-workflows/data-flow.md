# Data Flow

## Mapping

Mapping can be used to change the data flow behavior between the task and
workflow execution.

We differentiate between input and output mappings.
They are defined as extension elements on the corresponding task definition, see the following example.

```xml
  <serviceTask id="service">
      <extensionElements>
        <ioMapping>
          <input source="$.jsonObject" target="$.jsonObject"/>
          <output source="$.value" target="$" />
         </ioMapping>
      </extensionElements>
  </serviceTask>
```


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
in the source document and the `target` defines the path in the target document,
on which the value of the matching source should be written to. In the following section we dscribe shortly the json path expressions.

#### JSON Path expression

JSON Path expressions always refer to a JSON structure in the same way as XPath expression are used in combination with an XML document.
Since a JSON structure is usually anonymous and doesn't necessarily have a "root member object"
JSON Path assumes the abstract name `$` assigned to the outer level object. [1](http://goessner.net/articles/JsonPath/)

The payload will be on root level always a JSON Object.
JSON Path expressions can use the dot or bracket notation.
For example `$.prop` or `$[prop]` will match in the following JSON document and return the value `123`.

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

To match the `innerProp` we have to use one of the following expression `$.object.innerProp` or `$[object][innerProp]`.

Values in JSON Arrays can be accessed via square brackets and the corresponding index. Say we have the following expression `$.array[2]`
or `$[array][2]`.
This will match and return the value `5`, if we have for example the following JSON document.

```json
{
 "array" : [4, 3, 5]
}
```

**Note:**
In the following examples we will prefer the dot notation, since it is more readable.

#### Input Mapping Example

Input mappings extract content and copy it to an empty target payload.

In the following table some input mappings examples are listed, to give a overview of what is possible with the input mappings.
They all refer to the following source document:

```json
{
 "object": {
   "innerObj" : {
    "otherValue" : "String"
   },
  "value" : 123
 },
"array" : [ 1, 2, 3 ],
 "isExpected" : true
}
```
The result column contains the target document, which results after executing the input mapping.

<table>

  <tr><th>Source</th> <th>Target</th> <th>Result</th></tr>

  <tr>
    <td>$</td><td>$</td>
    <td><pre>
        {
         "object": {
           "innerObj" : {
            "otherValue" : "String"
           },
          "value" : 123
         },
        "array" : [ 1, 2, 3 ] ,
         "isExpected" : true
        }
    </pre></td>
  </tr>

  <tr>
    <td>$</td><td>$.newTarget</td>
    <td><pre>
        {
         "newTarget" : {
           "object": {
            "innerObj" : {
              "otherValue" : "String"
             },
            "value" : 123
           },
          "array" : [ 1, 2, 3 ] ,
           "isExpected" : true
          }
        }
    </pre></td>
  </tr>

  <tr>
    <td>$.object</td><td>$</td>
    <td><pre>
        {
          "innerObj" : {
              "otherValue" : "String"
          },
          "value" : 123
         }
    </pre></td>
  </tr>

  <tr>
    <td>$.object</td><td>$.newObj</td>
    <td><pre>
      {
        "newObj": {
            "innerObj" : {
              "otherValue" : "String"
             },
            "value" : 123
           }
      }
    </pre></td>
  </tr>

  <tr>
    <td>$.object.innerObj</td><td>$</td>
    <td><pre>
        {
          "otherValue" : "String"
        }
    </pre></td>
  </tr>


  <tr>
    <td>$.object.innerObj</td><td>$.newObj.newInner</td>
    <td><pre>
        {
          "newObj":
          {
            "newInner": {
              "otherValue" : "String"
            }
          }
        }
    </pre></td>
  </tr>


  <tr>
    <td>$.array</td><td>$.newArray</td>
    <td><pre>
        {
          "newArray" : [ 1, 2, 3 ]
        }
    </pre></td>
  </tr>

  <tr>
    <td>$.array[1]</td><td>$.newArray[0]</td>
    <td><pre>
        {
          "newArray" : [ 2 ]
        }
    </pre></td>
  </tr>

  <tr>
    <td>$.array[1]</td><td>$.newArray</td>
    <td><pre>
        {
          "newArray" : 2
        }
    </pre></td>
  </tr>

</table>

For more examples see the [extract mapping tests](https://github.com/zeebe-io/zb-msgpack-json-path/blob/master/json-path/src/test/java/io/zeebe/msgpack/mapping/MappingExtractParameterizedTest.java).

#### Output Mapping Example

Output mappings extract content of a source document and merge them with a target document.

In the following table some output mappings examples are listed, to give a overview of what is possible with output mappings.
They all refer to the following source and target documents.
The source could be the task payload and the target the workflow instance payload.

<table>

 <tr> <th>Source</th> <th>Target</th> </tr>
 <tr>
   <td>
      <pre>
        {
         "srcObject": {
           "innerObj" : {
            "otherValue" : "String"
           },
          "srcValue" : 123
         },
        "srcArray" : [ 1, 2, 3 ],
         "srcIsExpected" : true
        }
      </pre>
   </td>
   <td>
      <pre>
        {
         "object": {
           "innerObj" : {
            "otherValue" : "String"
           },
          "value" : 123
         },
        "array" : [ 1, 2, 3 ],
         "isExpected" : true
        }
      </pre>
   </td>
</table>

The result column contains the target document, which results after executing the output mapping

<table>

  <tr><th>Source</th> <th>Target</th> <th>Result</th></tr>

  <tr>
    <td>$</td><td>$</td>
    <td><pre>
      {
        "srcObject": {
          "innerObj" : {
            "otherValue" : "String"
          },
         "srcValue" : 123
        },
        "srcArray" : [ 1, 2, 3 ],
        "srcIsExpected" : true
      }
    </pre></td>
  </tr>

  <tr>
    <td>$</td><td>$.newTarget</td>
    <td><pre>
      {
        "newTarget" : {
          "srcObject": {
            "innerObj" : {
              "otherValue" : "String"
            },
            "srcValue" : 123
          },
          "srcArray" : [ 1, 2, 3 ],
          "srcIsExpected" : true
        },
        "object": {
          "innerObj" : {
            "otherValue" : "String"
          },
            "value" : 123
         },
        "array" : [ 1, 2, 3 ],
        "isExpected" : true
      }
    </pre></td>
  </tr>

  <tr>
    <td>$.srcObject</td><td>$</td>
    <td><pre>
        {
          "innerObj" : {
              "otherValue" : "String"
          },
          "srcValue" : 123
         }
    </pre></td>
  </tr>

  <tr>
    <td>$.srcObject</td><td>$.newObj</td>
    <td><pre>
      {
        "newObj": {
            "innerObj" : {
              "otherValue" : "String"
             },
            "srcValue" : 123
         },
         "object": {
           "innerObj" : {
            "otherValue" : "String"
           },
          "value" : 123
         },
        "array" : [ 1, 2, 3 ],
         "isExpected" : true
        }
    </pre></td>
  </tr>

  <tr>
    <td>$.srcObject</td><td>$.object.innerObj</td>
    <td><pre>
      {
        "object": {
          "innerObj" : {
            "innerObj" : {
              "otherValue" : "String"
            },
            "srcValue" : 123
           },
          "value" : 123
        },
        "array" : [ 1, 2, 3 ],
        "isExpected" : true
      }
    </pre></td>
  </tr>

  <tr>
    <td>$.srcObject.innerObj</td><td>$</td>
    <td><pre>
        {
          "otherValue" : "String"
        }
    </pre></td>
  </tr>


  <tr>
    <td>$.scrObject.innerObj</td><td>$.newObj.newInner</td>
    <td><pre>
        {
          "newObj":
          {
            "newInner": {
              "otherValue" : "String"
            }
          },
         "object": {
           "innerObj" : {
            "otherValue" : "String"
           },
          "value" : 123
         },
        "array" : [ 1, 2, 3 ],
        "isExpected" : true
      }
    </pre></td>
  </tr>


  <tr>
    <td>$.srcArray</td><td>$.newArray</td>
    <td><pre>
        {
          "newArray" : [ 1, 2, 3 ],
          "object": {
            "innerObj" : {
             "otherValue" : "String"
            },
           "value" : 123
         },
         "array" : [ 1, 2, 3 ],
         "isExpected" : true
       }
    </pre></td>
  </tr>

  <tr>
    <td>$.srcArray[1]</td><td>$.array[0]</td>
    <td><pre>
        {
          "object": {
            "innerObj" : {
             "otherValue" : "String"
            },
           "value" : 123
         },
         "array" : [ 2, 2, 3 ],
         "isExpected" : true
        }
    </pre></td>
  </tr>

  <tr>
    <td>$.array[1]</td><td>$.object.innerObj.otherValue</td>
    <td><pre>
        {
          "object": {
            "innerObj" : {
             "otherValue" : 2
            },
           "value" : 123
         },
         "array" : [ 1, 2, 3 ],
         "isExpected" : true
        }
    </pre></td>
  </tr>

</table>

For more examples see the [merge mapping tests](https://github.com/zeebe-io/zb-msgpack-json-path/blob/master/json-path/src/test/java/io/zeebe/msgpack/mapping/MappingMergeParameterizedTest.java).
