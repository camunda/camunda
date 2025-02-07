{
  "testResources": [],
  "testCases":[
    {
      "name":"request tracking code",
      "instructions":[
        {
          "name":"create-process-instance",
          "processId":"order-process",
          "variables":"{\"order_id\":\"order-2\"}",
          "processInstanceAlias":"process-instance"
        },
        {
          "name":"verify-element-instance-state",
          "processInstanceAlias":"process-instance",
          "elementId": "Activity_1b4p89b",
          "state":"completed"
        },
        {
          "name":"verify-element-instance-state",
          "processInstanceAlias":"process-instance",
          "elementId": "Activity_0ppua7y",
          "state":"completed"
        },
        {
          "name":"verify-element-instance-state",
          "processInstanceAlias":"process-instance",
          "elementId": "Activity_0fymy1k",
          "state":"completed"
        },
        {
          "name":"verify-element-instance-state",
          "processInstanceAlias":"process-instance",
          "elementId": "Activity_16rrgfy",
          "state":"active"
        },
        {
          "name":"increase-time",
          "duration": "P2D"
        },
        {
          "name":"verify-element-instance-state",
          "processInstanceAlias":"process-instance",
          "elementId": "Activity_0gclyi1",
          "state":"completed"
        },
        {
          "name":"verify-element-instance-state",
          "processInstanceAlias":"process-instance",
          "elementId": "Activity_16rrgfy",
          "state":"active"
        },
        {
          "name":"verify-process-instance-state",
          "processInstanceAlias":"process-instance",
          "state":"active"
        }
      ]
    }
  ]
}
